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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class ClienCrawler implements ICrawler {

    private final String baseUrl = "https://clien.net/service/board/park?&od=T31&category=0&po=%d";
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

//    @Override
//    public List<Post> crawl() {
//        return crawl(1);
//    }

    @Override
    public List<Post> crawl(LocalDateTime until) {
        List<Post> posts = new ArrayList<>();
        int page = 0;
        boolean continueCrawling = true;

        String[] userAgents = {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.5993.90 Safari/537.36",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_2_1) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.3 Safari/605.1.15",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        };

//      for (int page = 1; page <= pageCount; page++) {
        while (continueCrawling) {

            // --- 페이지 요청 간 무작위 지연 시간 추가 ---
            try {
                int randomDelay = (int) (Math.random() * 10000) + 1000; // 지연 로직
                double delaySeconds = randomDelay / 1000.0;
                log.info("페이지 요청 간 무작위 지연 시간 : " + delaySeconds + "ms");
                Thread.sleep(randomDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // ----------------------------------------------

            String url = String.format(baseUrl, page);
            try {
                String userAgent = userAgents[(int)(Math.random() * userAgents.length)];
                Document doc = Jsoup.connect(url)
                        .header("User-Agent", userAgent)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                        .timeout(10000) // 10초
                        .get();

                Elements postElements = doc.select("div.list_item.symph_row[data-role=list-row]");

                log.info("Clien " + page + "페이지 크롤링 결과: " + postElements.size());
//                log.info("Clien 크롤링 결과: " + postElements.size());

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

//                        int viewCount = Integer.parseInt(viewCountStr);

                        // 추천수 추출
                        Element recommendationElement = postElement.selectFirst("div.list_symph > span");
                        Integer recommendationCount = Optional.ofNullable(recommendationElement)
                                .map(Element::text)
                                .map(Integer::parseInt)
                                .orElse(0);

                        // 날짜/시간 추출
                        String dateString = postElement.selectFirst("span.time.popover > span.timestamp").text();
                        LocalDateTime createdAt = LocalDateTime.parse(dateString, formatter);

//                        // ✨ 게시글 날짜가 목표 날짜보다 이전이면 중단
//                        if (createdAt.isBefore(until)) {
//                            continueCrawling = false;
//                            break;
//                        }

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