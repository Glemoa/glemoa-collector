package hyunsub.glemoa.collector.service.impl;

import hyunsub.glemoa.collector.entity.Post;
import hyunsub.glemoa.collector.service.ICrawler;
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

@Component
public class PpomppuCrawler implements ICrawler {

    private final String url = "https://ppomppu.co.kr/hot.php?page=1&category=999";
    private final Pattern noPattern = Pattern.compile("no=(\\d+)");

    @Override
    public List<Post> crawl() {
        List<Post> posts = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .get();

            Elements postElements = doc.select("tr.baseList:not(.title_bg):not(.title_bg_03)");

            System.out.println("Ppomppu 크롤링 결과: " + postElements.size());

            for (Element postElement : postElements) {
                try {
                    // 광고성 게시글(AD)은 제외
                    if (postElement.selectFirst("#ad-icon") != null) {
                        continue;
                    }

//                    // 게시글 제목과 링크 추출
//                    Element titleElement = postElement.selectFirst("a.baseList-title");
//                    if (titleElement == null) {
//                        continue;
//                    }
//                    String title = titleElement.text();

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
                        System.err.println("경고: 게시글 번호(no)를 찾을 수 없습니다. 건너뜁니다. Link: " + link);
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