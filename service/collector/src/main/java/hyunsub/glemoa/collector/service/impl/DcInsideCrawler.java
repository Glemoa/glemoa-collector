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

@Slf4j
@Component
public class DcInsideCrawler implements ICrawler {
//    https://gall.dcinside.com/board/lists/?id=dcbest&page=1&_dcbest=9
//    private final String baseUrl = "https://gall.dcinside.com/board/lists/?id=dcbest&list_num=100&sort_type=N&search_head=6&page=%d";
    private final String baseUrl = "https://gall.dcinside.com/board/lists/?id=dcbest&page=%d&_dcbest=9";
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

                Elements postElements = doc.select("tr.ub-content.us-post");

                log.info("DcInside " + page + "페이지 크롤링 결과: " + postElements.size());

                for (Element postElement : postElements) {
                    String sourceIdElement = postElement.selectFirst("td.gall_num").text();
                    Element titleElement = postElement.selectFirst("td.gall_tit.ub-word a");
                    String title = titleElement.text();
                    String link = titleElement.attr("abs:href");
                    String author = postElement.selectFirst("td.gall_writer.ub-writer").attr("data-nick");
                    String commentsStr = postElement.selectFirst("a.reply_numbox").text();
                    String viewsStr = postElement.selectFirst("td.gall_count").text();
                    String recommendation = postElement.selectFirst("td.gall_recommend").text();

                    Long sourceId = Long.parseLong(sourceIdElement);
                    Integer commentCount = Integer.parseInt(commentsStr.replaceAll("[^0-9]", ""));
                    Integer viewCount = Integer.parseInt(viewsStr.replaceAll("[^0-9]", ""));
                    Integer recommendationCount = Integer.parseInt(recommendation.replaceAll("[^0-9]", ""));

                    String dateString = postElement.selectFirst("td.gall_date").attr("title");
                    LocalDateTime createdAt = LocalDateTime.parse(dateString, formatter);

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
                            .source("dcinside") // 출처를 명시합니다.
                            .build();

                    posts.add(post);
//                System.out.println(post.toString());
                }
            } catch (IOException e) {
                log.error("크롤링 중 오류가 발생했습니다: " + e.getMessage());
                e.printStackTrace();
                break;
            } catch (Exception e) {
                log.error("데이터 추출 중 오류가 발생했습니다: " + e.getMessage());
            }
            if (continueCrawling) {
                page++;
            }
        }

        return posts;
    }
}
