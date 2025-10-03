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
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class InvenCrawler implements ICrawler {

    private final String baseUrl = "https://www.inven.co.kr/board/webzine/2097?my=chu&p=%d";
    private final Pattern articleNoPattern = Pattern.compile("/board/webzine/2097/(\\d+)");

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

            String url = String.format(baseUrl, page);
            try {
                Document doc = Jsoup.connect(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .get();

                // 공지사항을 제외한 일반 게시글 목록 선택
                Elements postElements = doc.select("table.thumbnail tbody tr:not(.notice)");

                log.info("Inven " + page + " page 크롤링 결과: " + postElements.size());

                for (Element postElement : postElements) {
                    try {
                        // 제목, 링크 추출
                        Element subjectLinkElement = postElement.selectFirst("a.subject-link");
                        if (subjectLinkElement == null) {
                            continue;
                        }
                        String title = subjectLinkElement.text().trim();
                        String link = subjectLinkElement.attr("href");

                        // 게시글 고유 번호(sourceId) 추출
                        Matcher matcher = articleNoPattern.matcher(link);
                        Long sourceId = null;
                        if (matcher.find()) {
                            sourceId = Long.parseLong(matcher.group(1));
                        } else {
                            log.warn("경고: 링크에서 게시글 번호(sourceId)를 찾을 수 없습니다. 건너뜁니다. Link: " + link);
                            continue;
                        }

                        // 작성자 추출
                        Element userElement = postElement.selectFirst("td.user span.layerNickName");
                        String author = Optional.ofNullable(userElement)
                                .map(Element::text)
                                .orElse("익명");

                        // 댓글 수 추출
                        Element commentCountElement = postElement.selectFirst("span.con-comment");
                        int commentCount = Optional.ofNullable(commentCountElement)
                                .map(Element::text)
                                .map(s -> s.replaceAll("[^0-9]", ""))
                                .filter(s -> !s.isEmpty())
                                .map(Integer::parseInt)
                                .orElse(0);

                        // 조회 수, 추천 수 추출
                        String viewCountStr = postElement.selectFirst("td.view").text().replaceAll(",", "");
                        int viewCount = Integer.parseInt(viewCountStr.trim());

                        String recoCountStr = postElement.selectFirst("td.reco").text().replaceAll(",", "");
                        int recommendationCount = Integer.parseInt(recoCountStr.trim());

                        // 날짜/시간 추출
                        String timeStr = postElement.selectFirst("td.date").text().trim();
                        LocalDateTime createdAt;

                        if (timeStr.contains(":")) { // HH:mm format for today/yesterday
                            LocalTime postTime = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
                            LocalDate postDate = LocalDate.now();

                            // If the parsed time is in the future compared to now, it must be from yesterday
                            if (postTime.isAfter(LocalTime.now())) {
                                postDate = postDate.minusDays(1);
                            }
                            createdAt = LocalDateTime.of(postDate, postTime);

                        } else { // MM-dd format for older posts
                            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM-dd");
                            // Use MonthDay to parse just the month and day
                            MonthDay monthDay = MonthDay.parse(timeStr, dateFormatter);
                            // Apply the current year
                            LocalDate postDate = monthDay.atYear(LocalDate.now().getYear());

                            // If the parsed date is in the future, it must be from last year
                            if (postDate.isAfter(LocalDate.now())) {
                                postDate = postDate.minusYears(1);
                            }
                            createdAt = postDate.atStartOfDay();
                        }

                        // ✨ 게시글 날짜가 목표 날짜보다 이전이면 중단
                        if (createdAt.isBefore(until)) {
                            continueCrawling = false;
                            break;
                        }

                        Post post = Post.builder()
                                .sourceId(sourceId)
                                .title(title)
                                .link(link)
                                .author(author)
                                .commentCount(commentCount)
                                .viewCount(viewCount)
                                .recommendationCount(recommendationCount)
                                .createdAt(createdAt)
                                .source("inven")
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
                break;
            }
            if (continueCrawling) {
                page++;
            }
        }
        return posts;
    }
}