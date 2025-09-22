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
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//@Component
public class ArcaLiveCrawler implements ICrawler {

    private final String url = "https://arca.live/b/live?p=1";
    private final Pattern articleNoPattern = Pattern.compile("/b/live/(\\d+)");

    @Override
    public List<Post> crawl() {
        return crawl(1);
    }

    @Override
    public List<Post> crawl(int pageCount) {
        List<Post> posts = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(url)
                    .header("User-Agent", "live.arca.android/1.0.0")
                    .get();

            // 공지사항을 제외한 일반 게시글 목록 선택
            Elements postElements = doc.select("div.vrow.hybrid:not(.notice)");

            System.out.println("ArcaLive 크롤링 결과: " + postElements.size());

            for (Element postElement : postElements) {
                try {
                    // 제목, 링크, 게시글 번호(sourceId) 추출
                    Element titleElement = postElement.selectFirst("a.title.hybrid-title");
                    if (titleElement == null) {
                        continue;
                    }
                    String title = titleElement.text().trim();
                    String link = "https://arca.live" + titleElement.attr("href");

                    Matcher matcher = articleNoPattern.matcher(link);
                    Long sourceId = null;
                    if (matcher.find()) {
                        sourceId = Long.parseLong(matcher.group(1));
                    } else {
                        System.err.println("경고: 링크에서 게시글 번호(sourceId)를 찾을 수 없습니다. 건너뜁니다. Link: " + link);
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
                        System.err.println("경고: 조회수 파싱 오류: " + viewCountStr);
                    }

                    String recoCountStr = postElement.selectFirst("span.vcol.col-rate").text().trim().replaceAll(",", "");
                    int recommendationCount = 0;
                    try {
                        recommendationCount = Integer.parseInt(recoCountStr);
                    } catch (NumberFormatException e) {
                        System.err.println("경고: 추천수 파싱 오류: " + recoCountStr);
                    }

                    // 작성 시간 추출
                    String dateTimeStr = postElement.selectFirst("time[datetime]").attr("datetime");
                    LocalDateTime createdAt = ZonedDateTime.parse(dateTimeStr).toLocalDateTime();

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