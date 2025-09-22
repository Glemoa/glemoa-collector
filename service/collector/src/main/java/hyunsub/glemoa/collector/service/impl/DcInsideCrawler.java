package hyunsub.glemoa.collector.service.impl;

import hyunsub.glemoa.collector.entity.Post;
import hyunsub.glemoa.collector.service.ICrawler;
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

@Component
public class DcInsideCrawler implements ICrawler {
    private final String url = "https://gall.dcinside.com/board/lists/?id=dcbest&page=1&_dcbest=6";
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public List<Post> crawl() {
        List<Post> posts = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .get();

            Elements postElements = doc.select("tr.ub-content.us-post");

            System.out.println("DcInside 크롤링 결과: " +postElements.size());

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
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("데이터 추출 중 오류가 발생했습니다: " + e.getMessage());
        }
        return posts;
    }
}
