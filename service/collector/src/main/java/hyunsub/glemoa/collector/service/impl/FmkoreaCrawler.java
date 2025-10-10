package hyunsub.glemoa.collector.service.impl;

import hyunsub.glemoa.collector.entity.Post;
import hyunsub.glemoa.collector.service.ICrawler;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
@Component
public class FmkoreaCrawler implements ICrawler {

    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:126.0) Gecko/20100101 Firefox/126.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    );
    private static final Random RANDOM = new Random();
    private final String baseUrl = "https://www.fmkorea.com/index.php?mid=best&listStyle=list&page=%d";

    @Override
    public List<Post> crawl(LocalDateTime until) {
        List<Post> posts = new ArrayList<>();
        // WebDriverManager를 사용하여 ChromeDriver 자동 설정
//        WebDriverManager.chromedriver().setup();

        // 🚨 삽입 위치: 여기에 ChromeDriver 버전 명시 코드를 추가합니다.
//        WebDriverManager.chromedriver().browserVersion("104.0.5112.101").setup();

        // WebDriverManager를 사용하지 않음
        System.setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver");

        // Chrome 옵션 설정
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless"); // [디버깅] 브라우저 창을 띄우지 않는 헤드리스 모드
        options.addArguments("--disable-gpu"); // GPU 가속 비활성화 (일부 시스템에서 필요)
        options.addArguments("--no-sandbox"); // Sandbox 모드 비활성화 (Linux에서 필요할 수 있음)
        options.addArguments("--disable-dev-shm-usage"); // /dev/shm 사용 비활성화 (Linux에서 필요할 수 있음)
        options.addArguments("--remote-allow-origins=*"); // [추가] 최근 Chrome 정책 변경으로 인한 연결 문제 해결
        options.addArguments("user-agent=" + USER_AGENTS.get(RANDOM.nextInt(USER_AGENTS.size())));

        WebDriver driver = new ChromeDriver(options);

        try {

            int page = 1;
            boolean continueCrawling = true;

            while (continueCrawling) {
                String url = String.format(baseUrl, page);
                log.info("[FmkoreaCrawler] 페이지 방문 시도: {}", url);
                driver.get(url);

                // 페이지 로드를 기다리기 위한 암시적 대기 설정
                driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

                Document doc = Jsoup.parse(driver.getPageSource());
                Elements postElements = doc.select("table.bd_lst tbody tr:not(.notice)");

                log.info("Fmkorea " + page + "페이지 크롤링 결과: " + postElements.size());

                if (postElements.isEmpty()) {
                    log.info("[FmkoreaCrawler] 페이지 {}에 더 이상 게시글이 없어 크롤링을 중단합니다.", page);
                    break;
                }

                log.info("[FmkoreaCrawler] {} 페이지에서 {}개의 게시글 발견", page, postElements.size());

                for (Element postElement : postElements) {
                    try {
                        String linkHref = postElement.selectFirst("a.hx").attr("href");
                        String sourceIdElement = linkHref.replaceAll(".*document_srl=(\\d+).*", "$1");
                        Long sourceId = Long.parseLong(sourceIdElement);

                        String title = postElement.selectFirst("a.hx").text();
                        String link = "https://www.fmkorea.com" + linkHref;
                        String author = postElement.selectFirst("td.author a").text();
                        String recommendationCountStr = postElement.selectFirst("td.m_no.m_no_voted").text();
                        String viewCountStr = postElement.select("td.m_no").last().text();
                        String commentCountStr = postElement.selectFirst("a.replyNum").text();

                        String timeStr = postElement.selectFirst("td.time").text().trim();
                        LocalDateTime createdAt;

                        if (timeStr.contains(":")) { // HH:mm 형식
                            LocalTime postTime = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
                            LocalDate postDate = LocalDate.now();
                            if (postTime.isAfter(LocalTime.now())) {
                                postDate = postDate.minusDays(1);
                            }
                            createdAt = LocalDateTime.of(postDate, postTime);
                        } else { // yy.MM.dd or yyyy.MM.dd format
                            String[] dateParts = timeStr.split("\\.");
                            int year = Integer.parseInt(dateParts[0]);
                            if (year < 100) { // Handle 2-digit year
                                year += 2000;
                            }
                            int month = Integer.parseInt(dateParts[1]);
                            int day = Integer.parseInt(dateParts[2]);
                            createdAt = LocalDateTime.of(year, month, day, 0, 0);
                        }

                        if (until == null || !createdAt.isBefore(until)) {
                            Post post = Post.builder()
                                    .sourceId(sourceId)
                                    .title(title)
                                    .link(link)
                                    .author(author)
                                    .commentCount(Integer.parseInt(commentCountStr))
                                    .viewCount(Integer.parseInt(viewCountStr.replaceAll(",", "")))
                                    .recommendationCount(Integer.parseInt(recommendationCountStr.replaceAll(",", "")))
                                    .createdAt(createdAt)
                                    .source("fmkorea")
                                    .build();
                            posts.add(post);
                        } else {
                            log.info("[FmkoreaCrawler] 목표 날짜({}) 이전 게시글({})을 발견하여 크롤링을 중단합니다.", until, createdAt);
                            continueCrawling = false;
                            break;
                        }
                    } catch (Exception e) {
                        log.warn("[FmkoreaCrawler] 개별 게시글 처리 중 오류 발생: {}", e.getMessage());
                    }
                }

                if (continueCrawling) {
                    page++;
                    // 페이지 요청 간 무작위 지연
                    try {
                        Thread.sleep((long) (Math.random() * 1000) + 1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("[FmkoreaCrawler] 지연 중 스레드 인터럽트 발생", e);
                        continueCrawling = false;
                    }
                }
            }
        } catch (Exception e) {
            log.error("[FmkoreaCrawler] 크롤링 작업 중 심각한 오류 발생", e);
        } finally {
            // WebDriver 종료
            if (driver != null) {
                driver.quit();
                log.info("[FmkoreaCrawler] WebDriver를 종료합니다.");
            }
        }
        return posts;
    }
}
