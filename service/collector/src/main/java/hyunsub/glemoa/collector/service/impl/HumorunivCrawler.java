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
import java.util.regex.Pattern;

@Component
public class HumorunivCrawler implements ICrawler {

    private final String url = "https://web.humoruniv.com/board/humor/list.html?table=pds";
    private final Pattern sourceIdPattern = Pattern.compile("id=\"li_chk_pds-(\\d+)\"");

    @Override
    public List<Post> crawl() {
        List<Post> posts = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .get();

            Elements postElements = doc.select("table#post_list tbody tr[id^=li_chk_pds-]");


//            System.out.println(postElements);
            System.out.println("Humoruniv 크롤링 결과: " + postElements.size());

            for (Element postElement : postElements) {
                try {
                    // 게시글 고유 번호 (sourceId) 추출
                    String sourceIdStr = postElement.attr("id").replace("li_chk_pds-", "");
                    Long sourceId = Long.parseLong(sourceIdStr);

                    // 제목, 링크 추출
                    Element titleElement = postElement.selectFirst("a.li, a.brn1");
                    if (titleElement == null) {
                        continue;
                    }
                    String title = Optional.ofNullable(titleElement)
                            .map(el -> el.selectFirst("span"))
                            .map(Element::text)
                            .map(String::trim)
                            .orElse(null);

                    String link = "https://web.humoruniv.com/board/humor/" + titleElement.attr("href");

                    // 작성자 추출
                    Element authorElement = postElement.selectFirst("span.hu_nick_txt");
                    String author = Optional.ofNullable(authorElement).map(Element::text).orElse(null);

                    // 댓글 수 추출
                    Element commentCountElement = postElement.selectFirst("span.list_comment_num");
                    int commentCount = Optional.ofNullable(commentCountElement)
                            .map(Element::text)
                            .map(s -> s.replaceAll("[^0-9]", ""))
                            .filter(s -> !s.isEmpty())
                            .map(Integer::parseInt)
                            .orElse(0);

                    // 조회 수 추출
                    String viewCountStr = postElement.select("td.li_und").get(0).text().replaceAll(",", "");
                    int viewCount = Integer.parseInt(viewCountStr);

                    // 추천수 추출
                    String recommendationCountStr = postElement.selectFirst("span.o").text().replaceAll(",", "");
                    int recommendationCount = Integer.parseInt(recommendationCountStr);

                    // 날짜/시간 추출
                    String dateStr = postElement.selectFirst("span.w_date").text();
                    String timeStr = postElement.selectFirst("span.w_time").text();
                    LocalDateTime createdAt = LocalDateTime.parse(dateStr + " " + timeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

                    Post post = Post.builder()
                            .sourceId(sourceId)
                            .title(title)
                            .link(link)
                            .author(author)
                            .commentCount(commentCount)
                            .viewCount(viewCount)
                            .recommendationCount(recommendationCount)
                            .createdAt(createdAt)
                            .source("humoruniv")
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