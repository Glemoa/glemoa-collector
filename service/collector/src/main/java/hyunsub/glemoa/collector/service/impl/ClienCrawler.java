package hyunsub.glemoa.collector.service.impl;

import hyunsub.glemoa.collector.entity.Post;
import hyunsub.glemoa.collector.service.ICrawler;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Slf4j
@Component
public class ClienCrawler implements ICrawler {

    private final String baseUrl = "https://clien.net/service/board/park?&od=T31&category=0&po=%d";
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // [추가] FmkoreaCrawler에서 가져온 User-Agent 및 Random 객체
    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:126.0) Gecko/20100101 Firefox/126.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    );
    private static final Random RANDOM = new Random();

    @Override
    public List<Post> crawl(LocalDateTime until) {
        List<Post> posts = new ArrayList<>();
        int page = 0;
        boolean continueCrawling = true;

        // [수정] WebDriverManager를 사용하지 않고 시스템 속성 설정
        System.setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver");

        // [수정] Chrome 옵션 설정
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--remote-allow-origins=*");
        // 무작위 User-Agent 설정
        options.addArguments("user-agent=" + USER_AGENTS.get(RANDOM.nextInt(USER_AGENTS.size())));

        WebDriver driver = null;

        try {
            driver = new ChromeDriver(options);
            // 페이지 로드를 기다리기 위한 암시적 대기 설정 (FmkoreaCrawler 참고)
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

            while (continueCrawling) {
                // --- 페이지 요청 간 무작위 지연 시간 추가 (기존 로직 유지) ---
                try {
                    int randomDelay = (int) (Math.random() * 2000) + 1000; // 1초~3초 사이 지연으로 변경
                    double delaySeconds = randomDelay / 1000.0;
                    log.info("페이지 요청 간 무작위 지연 시간 : " + delaySeconds + "s");
                    Thread.sleep(randomDelay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                // ----------------------------------------------

                String url = String.format(baseUrl, page);
                log.info("[ClienCrawler] 페이지 방문 시도: {}", url);
                driver.get(url); // [수정] WebDriver로 페이지 로드

                // [수정] WebDriver가 로드한 페이지 소스를 Jsoup Document로 파싱
                Document doc = Jsoup.parse(driver.getPageSource());

                Elements postElements = doc.select("div.list_item.symph_row[data-role=list-row]");

                log.info("Clien " + page + "페이지 크롤링 결과: " + postElements.size());

                if (postElements.isEmpty() && page > 0) {
                    log.info("[ClienCrawler] 페이지 {}에 더 이상 게시글이 없어 크롤링을 중단합니다.", page);
                    break;
                }

                for (Element postElement : postElements) {
                    try {
                        // 게시글 고유 번호 (sourceId) 추출
                        String sourceIdStr = postElement.attr("data-board-sn");
                        Long sourceId = Long.parseLong(sourceIdStr);

                        // 제목, 링크 추출
                        Element titleElement = postElement.selectFirst("a.list_subject");
                        String title = titleElement.selectFirst("span.subject_fixed").text();
                        String link = "https://clien.net" + titleElement.attr("href");

                        // 작성자 추출
                        Element authorElement = postElement.selectFirst("span.nickname > span");
                        String author = Optional.ofNullable(authorElement)
                                .map(Element::text)
                                .orElse(null);

                        // 댓글 수 추출
                        Element commentCountElement = postElement.selectFirst("a.list_reply > span.rSymph05");
                        int commentCount = Optional.ofNullable(commentCountElement)
                                .map(Element::text)
                                .map(Integer::parseInt)
                                .orElse(0);

                        // 조회 수 추출
                        String viewCountStr = postElement.selectFirst("span.hit").text().replaceAll(",", "");
                        int viewCount = 0;
                        if (viewCountStr.endsWith("k")) {
                            double parsedValue = Double.parseDouble(viewCountStr.replace("k", "").trim());
                            viewCount = (int) (parsedValue * 1000);
                        } else {
                            viewCount = Integer.parseInt(viewCountStr);
                        }

                        // 추천수 추출
                        Element recommendationElement = postElement.selectFirst("div.list_symph > span");
                        Integer recommendationCount = Optional.ofNullable(recommendationElement)
                                .map(Element::text)
                                .map(s -> s.replaceAll("[^0-9]", "")) // ✨ FIX: 숫자 외 문자(예: '99+') 제거
                                .filter(s -> !s.isEmpty()) // 빈 문자열 파싱 방지
                                .map(Integer::parseInt)
                                .orElse(0);

                        // 날짜/시간 추출
                        String dateString = postElement.selectFirst("span.time.popover > span.timestamp").text();
                        LocalDateTime createdAt = LocalDateTime.parse(dateString, formatter);

                        // 💡 수정된 로직: 목표 날짜 이후의 게시글만 추가
                        if (!createdAt.isBefore(until)) {
                            Post post = Post.builder()
                                    .sourceId(sourceId)
                                    .title(title)
                                    .link(link)
                                    .author(author)
                                    .commentCount(commentCount)
                                    .viewCount(viewCount)
                                    .recommendationCount(recommendationCount)
                                    .createdAt(createdAt)
                                    .source("clien")
                                    .build();
                            posts.add(post);
                        } else {
                            // 목표 날짜에 도달하면 크롤링 중단
                            log.info("목표 날짜에 도달하여 크롤링을 중단합니다.");
                            continueCrawling = false;
                            break;
                        }

                    } catch (Exception e) {
                        log.warn("개별 게시글 크롤링 중 오류가 발생했습니다: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                // 페이지 증가는 루프의 끝에서 처리
                if (continueCrawling) {
                    page++;
                }
            }
        } catch (Exception e) {
            log.error("[ClienCrawler] 크롤링 작업 중 오류 발생: {}", e.getMessage(), e);
        } finally {
            // [수정] WebDriver 종료
            if (driver != null) {
                driver.quit();
                log.info("[ClienCrawler] WebDriver를 종료합니다.");
            }
        }
        return posts;
    }
}