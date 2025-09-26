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
public class EtolandCrawler implements ICrawler {

    private final String baseUrl = "https://etoland.co.kr/bbs/hit.php?limit=50&page=%d";
    private final Pattern sourceIdPattern = Pattern.compile("bn_id=(\\d+)");

    @Override
    public List<Post> crawl(LocalDateTime until) {
        List<Post> posts = new ArrayList<>();
        int page = 1;
        boolean continueCrawling = true;

        while (continueCrawling) {

            // --- 페이지 요청 간 무작위 지연 시간 추가 ---
            try {
                int randomDelay = (int) (Math.random() * 2000) + 1000; // 2초~8초 사이 지연
                double delaySeconds = randomDelay / 1000.0;
                log.info("페이지 요청 간 무작위 지연 시간 : " + delaySeconds + " s");
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

                // 광고 및 이벤트 링크를 제외한 게시글만 선택하도록 셀렉터 수정
                Elements postElements = doc.select("ul#hit_list li.hit_item:not(.ad_list):not(.power_link-list)");

                log.info("Etoland " + page + "페이지 크롤링 결과: " + postElements.size());

                for (Element postElement : postElements) {
                    try {

                        // ✨ 광고성 게시글이면 건너뛰기
                        if (postElement.hasClass("ad_list")) {
                            continue;
                        }

                        // 게시글 제목과 링크, 고유 번호(sourceId) 추출
                        Element contentLink = postElement.selectFirst("a.content_link");
                        if (contentLink == null) {
                            continue;
                        }

                        String link = "https://etoland.co.kr" + contentLink.attr("href");

                        // 링크가 유효한 게시글 URL인지 확인
                        Matcher matcher = sourceIdPattern.matcher(link);

                        Long sourceId = null;
                        if (matcher.find()) {
                            sourceId = Long.parseLong(matcher.group(1));
                        } else {
                            log.warn("경고: 게시글 번호(bn_id)를 찾을 수 없습니다. 건너뜁니다. Link: " + link);
                            continue; // 유효한 게시글 링크가 아니면 다음 항목으로 넘어감
                        }

                        Element subjectElement = contentLink.selectFirst("p.subject");
                        String title = Optional.ofNullable(subjectElement).map(Element::text).map(String::trim).orElse(null);


                        // 작성자 추출
                        Element authorElement = postElement.selectFirst("span.nick");
                        String author = Optional.ofNullable(authorElement).map(Element::text).map(String::trim).orElse(null);

                        // 댓글 수 추출
                        Element commentCountElement = postElement.selectFirst("span.comment_cnt");
                        int commentCount = Optional.ofNullable(commentCountElement)
                                .map(Element::text)
                                .map(s -> s.replaceAll("[^0-9]", ""))
                                .filter(s -> !s.isEmpty())
                                .map(Integer::parseInt)
                                .orElse(0);

                        // 조회수 추출
                        String viewCountStr = postElement.selectFirst("span.hit").text();
                        int viewCount = Integer.parseInt(viewCountStr.replaceAll("[^0-9]", ""));

                        // 추천수 추출
                        String recommendationCountStr = postElement.selectFirst("span.good").text();
                        int recommendationCount = Integer.parseInt(recommendationCountStr.replaceAll("[^0-9]", ""));

                        // 날짜/시간 추출 (상대 시간으로 표시되어 현재 시간 기준으로 계산)
                        String timeStr = postElement.selectFirst("span.datetime").text().trim();
                        LocalDateTime createdAt = LocalDateTime.now();
                        if (timeStr.equals("방금")) {
                            createdAt = LocalDateTime.now();
                        } else if (timeStr.endsWith("분전")) {
                            int minutesAgo = Integer.parseInt(timeStr.replaceAll("[^0-9]", ""));
                            createdAt = createdAt.minusMinutes(minutesAgo);
                        } else if (timeStr.endsWith("시간전")) {
                            int hoursAgo = Integer.parseInt(timeStr.replaceAll("[^0-9]", ""));
                            createdAt = createdAt.minusHours(hoursAgo);
                        } else if (timeStr.endsWith("일전")) {
                            int daysAgo = Integer.parseInt(timeStr.replaceAll("[^0-9]", ""));
                            createdAt = createdAt.minusDays(daysAgo);
                        } else {
                            try {
                                // "yyyy-MM-dd HH:mm" 형태의 포맷을 먼저 시도
                                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                                createdAt = LocalDateTime.parse(timeStr, formatter);
                            } catch (DateTimeParseException dateEx) {
                                try {
                                    // "MM-dd" 형식 → 올해 연도로 보정
//                                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
//                                    LocalDate localDate = LocalDate.parse(timeStr, formatter);
//                                    localDate = localDate.withYear(LocalDate.now().getYear()); // 올해 연도 붙이기
//                                    createdAt = localDate.atStartOfDay(); // 00시 기준으로 LocalDateTime 생성

                                    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
                                    MonthDay md = MonthDay.parse(timeStr, fmt);
                                    LocalDate localDate = md.atYear(LocalDate.now().getYear());
                                    createdAt = localDate.atStartOfDay();

                                } catch (DateTimeParseException monthDayEx) {
                                    // 다른 모든 형식에서 실패하면 로그를 남기고 현재 시간으로 처리
                                    log.warn("알 수 없는 날짜 형식: {}", timeStr);
                                    createdAt = LocalDateTime.now();
                                }
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
                                .link(link)
                                .author(author)
                                .commentCount(commentCount)
                                .viewCount(viewCount)
                                .recommendationCount(recommendationCount)
                                .createdAt(createdAt)
                                .source("Etoland") // 출처를 명시합니다.
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
