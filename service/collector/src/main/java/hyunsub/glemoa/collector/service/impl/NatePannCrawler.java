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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//@Component
public class NatePannCrawler implements ICrawler {

    private final String url = "https://pann.nate.com/talk/ranking";
    private final String baseUrl = "https://pann.nate.com";
    private final Pattern articleNoPattern = Pattern.compile("/talk/(\\d+)");

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

            Elements postElements = doc.select("ul.post_wrap li");

            System.out.println("NatePann 크롤링 결과: " + postElements.size());

            for (Element postElement : postElements) {
                try {
                    // 제목, 링크 추출
                    Element titleLinkElement = postElement.selectFirst("dl dt h2 a");
                    if (titleLinkElement == null) {
                        continue;
                    }
                    String title = titleLinkElement.text().trim();
                    String link = baseUrl + titleLinkElement.attr("href");

                    // 게시글 고유 번호(sourceId) 추출
                    Matcher matcher = articleNoPattern.matcher(link);
                    Long sourceId = null;
                    if (matcher.find()) {
                        sourceId = Long.parseLong(matcher.group(1));
                    } else {
                        System.err.println("경고: 링크에서 게시글 번호(sourceId)를 찾을 수 없습니다. 건너뜁니다. Link: " + link);
                        continue;
                    }

                    // 댓글 수 추출
                    Element commentCountElement = postElement.selectFirst("dt span.reple-num");
                    int commentCount = Optional.ofNullable(commentCountElement)
                            .map(Element::text)
                            .map(s -> s.replaceAll("[^0-9]", ""))
                            .filter(s -> !s.isEmpty())
                            .map(Integer::parseInt)
                            .orElse(0);

                    // 조회 수, 추천 수 추출
                    String viewCountStr = postElement.selectFirst("dd.info span.count").text().replaceAll("[^0-9]", "");
                    int viewCount = 0;
                    try {
                        viewCount = Integer.parseInt(viewCountStr.trim());
                    } catch (NumberFormatException e) {
                        System.err.println("경고: 조회수 파싱 오류: " + viewCountStr);
                    }

                    String recoCountStr = postElement.selectFirst("dd.info span.rcm").text().replaceAll("[^0-9]", "");
                    int recommendationCount = 0;
                    try {
                        recommendationCount = Integer.parseInt(recoCountStr.trim());
                    } catch (NumberFormatException e) {
                        System.err.println("경고: 추천수 파싱 오류: " + recoCountStr);
                    }

                    Post post = Post.builder()
                            .sourceId(sourceId)
                            .title(title)
                            .link(link)
                            .author("익명") // 작성자 정보가 없어 익명으로 처리
                            .commentCount(commentCount)
                            .viewCount(viewCount)
                            .recommendationCount(recommendationCount)
                            .createdAt(LocalDateTime.now()) // 날짜/시간 정보가 없어 현재 시점으로 처리
                            .source("pann")
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