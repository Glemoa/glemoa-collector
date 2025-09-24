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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ArcaLiveCrawler implements ICrawler {

//    private final String url = "https://arca.live/b/live?p=1";
    private final String baseUrl = "https://arca.live/b/live?p=%d";
    private final Pattern articleNoPattern = Pattern.compile("/b/live/(\\d+)");

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
                int randomDelay = (int) (Math.random() * 5000) + 1000; // 1초~3초 사이 지연
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
                        .header("User-Agent", "live.arca.android/1.0.0")
                        .get();

                // 공지사항을 제외한 일반 게시글 목록 선택
                Elements postElements = doc.select("div.vrow.hybrid:not(.notice)");

                log.info("ArcaLive " + page + "페이지 크롤링 결과: " + postElements.size());

                for (Element postElement : postElements) {
                    try {
                        // 제목, 링크, 게시글 번호(sourceId) 추출
                        Element titleElement = postElement.selectFirst("a.title.hybrid-title");
                        if (titleElement == null) {
                            continue;
                        }
                        String title = titleElement.text().trim();
                        String link = titleElement.attr("href");

                        Matcher matcher = articleNoPattern.matcher(link);
                        Long sourceId = null;
                        if (matcher.find()) {
                            sourceId = Long.parseLong(matcher.group(1));
                        } else {
                            log.warn("경고: 링크에서 게시글 번호(sourceId)를 찾을 수 없습니다. 건너뜁니다. Link: " + link);
                            continue;
                        }

                        // 작성자 추출
                        Element authorElement = postElement.selectFirst("span.vcol.col-author span.user-info span[data-filter]");
                        String author = Optional.ofNullable(authorElement)
                                .map(Element::text)
                                .orElse("익명");

                        // 댓글 수 추출
                        Element commentCountElement = postElement.selectFirst("span.comment-count");
                        int commentCount = Optional.ofNullable(commentCountElement)
                                .map(Element::text)
                                .map(s -> s.replaceAll("[^0-9]", ""))
                                .filter(s -> !s.isEmpty())
                                .map(Integer::parseInt)
                                .orElse(0);

                        // 조회 수, 추천 수 추출
                        String viewCountStr = postElement.selectFirst("span.vcol.col-view").text().trim().replaceAll(",", "");
                        int viewCount = 0;
                        try {
                            viewCount = Integer.parseInt(viewCountStr);
                        } catch (NumberFormatException e) {
                            log.warn("경고: 조회수 파싱 오류: " + viewCountStr);
                        }

                        String recoCountStr = postElement.selectFirst("span.vcol.col-rate").text().trim().replaceAll(",", "");
                        int recommendationCount = 0;
                        try {
                            recommendationCount = Integer.parseInt(recoCountStr);
                        } catch (NumberFormatException e) {
                            log.warn("경고: 추천수 파싱 오류: " + recoCountStr);
                        }

                        // 작성 시간 추출 (datetime 속성을 우선적으로 사용)
                        LocalDateTime createdAt;
                        try {
                            String dateTimeAttr = postElement.selectFirst("time[datetime]").attr("datetime");
                            createdAt = ZonedDateTime.parse(dateTimeAttr).toLocalDateTime();
                        } catch (Exception dateEx) {
                            // datetime 속성이 없거나 파싱 실패 시, 태그의 텍스트를 파싱
                            String timeStr = postElement.selectFirst("span.vcol.col-time").text().trim();
                            if (timeStr.contains("시간전")) {
                                int hoursAgo = Integer.parseInt(timeStr.replaceAll("[^0-9]", ""));
                                createdAt = LocalDateTime.now().minusHours(hoursAgo);
                            } else if (timeStr.contains("분전")) {
                                int minutesAgo = Integer.parseInt(timeStr.replaceAll("[^0-9]", ""));
                                createdAt = LocalDateTime.now().minusMinutes(minutesAgo);
                            } else if (timeStr.matches("\\d{2}-\\d{2}")) { // MM-dd 형식 처리
                                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
                                LocalDate localDate = LocalDate.parse(timeStr, formatter);
                                createdAt = localDate.atTime(LocalTime.now());
                            } else {
                                // 다른 형식의 날짜가 나타나면 현재 시간으로 처리
                                createdAt = LocalDateTime.now();
                            }
                        }

                        // ✨ 게시글 날짜가 목표 날짜보다 이전이면 중단
                        if (createdAt.isBefore(until)) {
                            continueCrawling = false;
                            break;
                        }

                        Post post = Post.builder()
                                .sourceId(sourceId)
                                .title(title)
                                .link("https://arca.live" + link)
                                .author(author)
                                .commentCount(commentCount)
                                .viewCount(viewCount)
                                .recommendationCount(recommendationCount)
                                .createdAt(createdAt)
                                .source("arcalive")
                                .build();

                        posts.add(post);
                        //                    System.out.println(post.toString());

                    } catch (Exception e) {
                        log.warn("개별 게시글 크롤링 중 오류가 발생했습니다: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                log.warn("크롤링 중 오류가 발생했습니다: " + e.getMessage());
                e.printStackTrace();
            }
            if (continueCrawling) {
                page++;
            }
        }
        return posts;
    }
}