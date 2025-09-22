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
import java.util.Optional;

//@Component
public class ClienCrawler implements ICrawler {

    private final String url = "https://clien.net/service/board/park?&od=T31&category=0&po=0";
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public List<Post> crawl() {
        return crawl(1);
    }

    @Override
    public List<Post> crawl(int pageCount) {
        List<Post> posts = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .get();

            Elements postElements = doc.select("div.list_item.symph_row[data-role=list-row]");

            System.out.println("Clien 크롤링 결과: " + postElements.size());

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
                    int viewCount = Integer.parseInt(viewCountStr);

                    // 추천수 추출
                    Element recommendationElement = postElement.selectFirst("div.list_symph > span");
                    Integer recommendationCount = Optional.ofNullable(recommendationElement)
                            .map(Element::text)
                            .map(Integer::parseInt)
                            .orElse(0);

                    // 날짜/시간 추출
                    String dateString = postElement.selectFirst("span.time.popover > span.timestamp").text();
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
                            .source("clien")
                            .build();

                    posts.add(post);
//                    System.out.println(post.toString());
                } catch (Exception e) {
                    System.err.println("개별 게시글 크롤링 중 오류가 발생했습니다: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.err.println("크롤링 중 오류가 발생했습니다: " + e.getMessage());
            e.printStackTrace();
        }
        return posts;
    }
}