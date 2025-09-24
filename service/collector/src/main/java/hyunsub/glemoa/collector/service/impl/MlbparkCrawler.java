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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class MlbparkCrawler implements ICrawler {
//    private final String baseUrl = "https://mlbpark.donga.com/mp/b.php?p=%d&m=list&b=bullpen&query=&select=&subquery=&subselect=&user=";
    private final String baseUrl = "https://mlbpark.donga.com/mp/honor.php?p=%d&b=bullpen&h=burning&ranking=real&m=list&query=&select=&subquery=&subselect=&user=";
    private final String baseMlbparkUrl = "https://mlbpark.donga.com";
    private final Pattern articleIdPattern = Pattern.compile("id=(\\d+)");
    private final Random random = new Random();

//    @Override
//    public List<Post> crawl() {
//        return crawl(1);
//    }

    @Override
    public List<Post> crawl(LocalDateTime until) {
        List<Post> posts = new ArrayList<>();
        int page = 1;
        boolean continueCrawling = true;

//      for (int page = 1; page <= pageCount; page++) {
        while (continueCrawling) {

            // --- 페이지 요청 간 무작위 지연 시간 추가 ---
            try {
                int randomDelay = (int) (Math.random() * 2000) + 1000; // 1초~3초 사이 지연
                double delaySeconds = randomDelay / 1000.0;
                log.info("페이지 요청 간 무작위 지연 시간 : " + delaySeconds + "ms");
                Thread.sleep(randomDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // ----------------------------------------------

            // 페이지 번호 계산: 1, 31, 61, 91...
            String url = String.format(baseUrl, (page - 1) * 30 + 1);

            try {
                Document doc = Jsoup.connect(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .get();

//                Elements postElements = doc.select("table.tbl_type01 tbody tr");

                // HTML 구조 변경: "table.tbl_type01 tbody tr" 대신 "ul.gather_list li.items"로 변경
                Elements postElements = doc.select("div.gather_wrap ul.gather_list li.items");

                if (postElements.isEmpty()) {
                    log.info("더 이상 게시글이 없습니다. 크롤링을 종료합니다.");
                    break;
                }
//                log.info("Etoland " + page + "페이지 크롤링 결과: " + postElements.size());
                log.info("Mlbpark " + page + "페이지 크롤링 결과: " + postElements.size());

                for (Element postElement : postElements) {
                    try {
                        // 제목, 링크, 게시글 ID 추출
                        Element titleElement = postElement.selectFirst("div.title a");
                        if (titleElement == null) {
                            continue;
                        }
                        String title = titleElement.text().trim();
                        String link = titleElement.attr("href");

                        Matcher matcher = articleIdPattern.matcher(link);
                        Long sourceId = null;
                        if (matcher.find()) {
                            sourceId = Long.parseLong(matcher.group(1));
                        } else {
                            log.warn("경고: 링크에서 게시글 ID(sourceId)를 찾을 수 없습니다. 건너뜁니다. Link: " + link);
                            continue;
                        }

                        // 작성자 추출: "span.nick" 대신 "span.user_name" 사용
                        String author = Optional.ofNullable(postElement.selectFirst("div.info span.user_name"))
                                .map(Element::text)
                                .orElse("익명");

                        // 댓글 수 추출: "span.replycont"에서 대괄호 제거 후 숫자만 파싱
                        String commentCountStr = Optional.ofNullable(postElement.selectFirst("span.replycont"))
                                .map(Element::text)
                                .orElse("[0]")
                                .replaceAll("[^0-9]", "");
                        int commentCount = commentCountStr.isEmpty() ? 0 : Integer.parseInt(commentCountStr);

                        // 날짜/시간 추출: "div.info span.date"의 텍스트를 파싱
                        String timeStr = Optional.ofNullable(postElement.selectFirst("div.info span.date"))
                                .map(Element::text)
                                .orElse("");

                        LocalDateTime createdAt = null;
                        if (timeStr.matches("\\d{2}:\\d{2}:\\d{2}")) { // HH:mm:ss 형식
                            createdAt = LocalDateTime.of(LocalDate.now(), LocalTime.parse(timeStr));
                        } else if (timeStr.matches("\\d{4}-\\d{2}-\\d{2}")) { // yyyy-MM-dd 형식
                            createdAt = LocalDate.parse(timeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay();
                        } else {
                            log.warn("경고: 날짜/시간 형식을 파싱할 수 없습니다. 현재 시간으로 처리합니다. TimeStr: " + timeStr);
                            createdAt = LocalDateTime.now();
                        }

                        // ✨ 게시글 날짜가 목표 날짜보다 이전이면 크롤링 중단
                        if (createdAt.isBefore(until)) {
                            continueCrawling = false;
                            log.info("목표 날짜에 도달하여 크롤링을 중단합니다.");
                            break;
                        }

                        Post post = Post.builder()
                                .sourceId(sourceId)
                                .title(title)
                                .link(link) // 링크 형식 통일
                                .author(author)
                                .commentCount(commentCount)
                                .viewCount(null) // 해당 HTML 구조에는 조회수가 없습니다.
                                .recommendationCount(null) // 해당 HTML 구조에는 추천수가 없습니다.
                                .createdAt(createdAt)
                                .source("mlbpark")
                                .build();

                        posts.add(post);
//                    System.out.println(post.toString());

                    } catch (Exception e) {
                        log.warn("개별 게시글 크롤링 중 오류가 발생했습니다: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                log.error("크롤링 중 오류가 발생했습니다: " + e.getMessage());
                e.printStackTrace();
            }
            if (continueCrawling) {
                page++;
            }
        }
        return posts;
    }
}
