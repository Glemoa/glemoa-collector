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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class PpomppuCrawler implements ICrawler {
//    https://ppomppu.co.kr/hot.php?id=freeboard&page=1&category=999&page_num=1
    private final String baseUrl = "https://ppomppu.co.kr/hot.php?id=freeboard&page=%d&category=999&page_num=1";
    private final Pattern noPattern = Pattern.compile("no=(\\d+)");

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

                Elements postElements = doc.select("tr.baseList:not(.title_bg):not(.title_bg_03)");

                log.info("Ppomppu " + page + "페이지 크롤링 결과: " + postElements.size());
//                log.info("Ppomppu 크롤링 결과: " + postElements.size());

                for (Element postElement : postElements) {
                    try {
                        // 광고성 게시글(AD)은 제외
                        if (postElement.selectFirst("#ad-icon") != null) {
                            continue;
                        }

                        // 게시글 제목과 링크 추출
                        Elements titleElements = postElement.select("a.baseList-title");
                        if (titleElements.size() < 2) {
                            // 제목이 없거나 구조가 다른 경우
                            continue;
                        }

                        // 두 번째 <a> 태그에서 제목 텍스트를 가져옵니다.
                        String title = titleElements.get(1).text();
                        String link = titleElements.attr("href");

                        // 게시글 고유 번호(no) 추출
                        Matcher matcher = noPattern.matcher(link);
                        Long sourceId = null;
                        if (matcher.find()) {
                            sourceId = Long.parseLong(matcher.group(1));
                        } else {
                            log.warn("경고: 게시글 번호(no)를 찾을 수 없습니다. 건너뜁니다. Link: " + link);
                            continue;
                        }

                        // 댓글 수 추출
                        Element commentCountElement = postElement.selectFirst("span.list_comment2");
                        int commentCount = Optional.ofNullable(commentCountElement)
                                .map(Element::text)
                                .map(s -> s.replaceAll("[^0-9]", ""))
                                .filter(s -> !s.isEmpty())
                                .map(Integer::parseInt)
                                .orElse(0);

                        // 작성자, 추천수, 조회수 추출
                        String author = postElement.selectFirst("div.list_name").text();
                        String recommendationText = postElement.select("td.baseList-space.board_date").get(1).text();
                        int recommendationCount = Integer.parseInt(recommendationText.split(" ")[0]);
                        int viewCount = Integer.parseInt(postElement.select("td.baseList-space.board_date").get(2).text().replaceAll(",", ""));

                        // 날짜/시간 추출
                        String timeStr = postElement.select("td.baseList-space.board_date").get(0).text().trim();
                        LocalDateTime createdAt;
                        try {
                            // 날짜가 포함된 경우 (예: 22/10/02)
                            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yy/MM/dd");
                            createdAt = LocalDate.parse(timeStr, dateFormatter).atStartOfDay();
                        } catch (DateTimeParseException e) {
                            // 시간만 포함된 경우 (예: 20:57:01)
                            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
                            createdAt = LocalDateTime.of(LocalDate.now(), LocalTime.parse(timeStr, timeFormatter));
                        }

                        // ✨ 게시글 날짜가 목표 날짜보다 이전이면 중단
                        if (createdAt.isBefore(until)) {
                            continueCrawling = false;
                            break;
                        }

                        Post post = Post.builder()
                                .sourceId(sourceId)
                                .title(title)
                                .link("https://www.ppomppu.co.kr" + link)
                                .author(author)
                                .commentCount(commentCount)
                                .viewCount(viewCount)
                                .recommendationCount(recommendationCount)
                                .createdAt(createdAt)
                                .source("ppomppu")
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