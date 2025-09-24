package hyunsub.glemoa.collector.service.impl;

import hyunsub.glemoa.collector.entity.Post;
import hyunsub.glemoa.collector.service.ICrawler;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j
@Component
public class FmkoreaCrawler implements ICrawler {

    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:126.0) Gecko/20100101 Firefox/126.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 Edg/124.0.0.0"
    );
    private static final Random RANDOM = new Random();

    private final String baseUrl = "https://www.fmkorea.com/index.php?mid=best&listStyle=list&page=%d";
    private final String mainUrl = "https://www.fmkorea.com/";

    private Map<String, String> cookies;
    private LocalDateTime cookieRefreshTime;

    private void refreshCookies(String userAgent) throws IOException {
        if (cookies == null || cookieRefreshTime == null || LocalDateTime.now().isAfter(cookieRefreshTime)) {
            log.info("fmkorea.com 쿠키가 없거나 만료되어 갱신을 시도합니다.");
            this.cookies = Jsoup.connect(mainUrl)
                    .userAgent(userAgent)
                    .execute()
                    .cookies();
            this.cookieRefreshTime = LocalDateTime.now().plusMinutes(10);
            log.info("fmkorea.com 쿠키 갱신 완료. 다음 갱신 예정: {}", this.cookieRefreshTime);
        }
    }

    @Override
    public List<Post> crawl(LocalDateTime until) {
        List<Post> posts = new ArrayList<>();
        int page = 1;
        boolean continueCrawling = true;

        String currentUserAgent = USER_AGENTS.get(RANDOM.nextInt(USER_AGENTS.size()));
        log.info("Using User-Agent for this session: {}", currentUserAgent);

        try {
            refreshCookies(currentUserAgent);

            while (continueCrawling) {
                try {
                    int randomDelay = (int) (Math.random() * 4000) + 1000; // 3초~7초 사이 지연
                    double delaySeconds = randomDelay / 1000.0;
                    log.info("페이지 요청 간 무작위 지연 시간 : {}s", delaySeconds);
                    Thread.sleep(randomDelay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                String url = String.format(baseUrl, page);
                try {
                    Document doc = Jsoup.connect(url)
                            .userAgent(currentUserAgent)
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                            .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                            .header("Referer", mainUrl)
                            .cookies(this.cookies) // 클래스 멤버 변수 쿠키 사용
                            .timeout(10000)
                            .get();

                    Elements postElements = doc.select("table.bd_lst tbody tr:not(.notice)");

                    if (postElements.isEmpty()) {
                        log.info("페이지 {}에 더 이상 게시글이 없어 크롤링을 중단합니다.", page);
                        continueCrawling = false;
                        continue;
                    }

                    log.info("Fmkorea " + page + "페이지 크롤링 결과: " + postElements.size());

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

                            if (timeStr.contains(".")) { // yy.MM.dd 형식
                                String[] dateParts = timeStr.split("\\.");
                                int year = Integer.parseInt(dateParts[0]);
                                int month = Integer.parseInt(dateParts[1]);
                                int day = Integer.parseInt(dateParts[2]);
                                createdAt = LocalDateTime.of(year, month, day, 0, 0);
                            } else { // HH:mm 형식
                                LocalTime postTime = LocalTime.parse(timeStr);
                                LocalDate postDate = LocalDate.now();

                                if (postTime.isAfter(LocalTime.now())) {
                                    postDate = postDate.minusDays(1);
                                }
                                createdAt = LocalDateTime.of(postDate, postTime);
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
//                                log.info(post.toString());
                                posts.add(post);
                            } else {
                                log.info("목표 날짜({}) 이전 게시글({})을 발견하여 크롤링을 중단합니다.", until, createdAt);
                                continueCrawling = false;
                                break;
                            }

                        } catch (Exception e) {
                            log.warn("개별 게시글 크롤링 중 오류가 발생했습니다: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                } catch (IOException e) {
                    log.error("크롤링 중 오류가 발생했습니다: " + e.getMessage());
                    e.printStackTrace();
                    continueCrawling = false;
                }
                if (continueCrawling) {
                    page++;
                }
            }
        } catch (IOException e) {
            log.error("크롤링 초기화 중 오류 발생 (쿠키 갱신 실패): " + e.getMessage());
            this.cookies = null; // 실패 시 쿠키를 null로 설정하여 다음 시도 시 갱신하도록 함
            e.printStackTrace();
        }
        return posts;
    }
}