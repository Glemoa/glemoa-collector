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
public class BobaedreamCrawler implements ICrawler {

    private final String url = "https://www.bobaedream.co.kr/board/bulletin/list.php?code=best";
    private final Pattern sourceIdPattern = Pattern.compile("No=(\\d+)");

    @Override
    public List<Post> crawl() {
        List<Post> posts = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .get();

            Elements postElements = doc.select("table#boardlist tbody tr[itemscope]");

            System.out.println("Bobaedream 크롤링 결과: " + postElements.size());

            for (Element postElement : postElements) {
                try {
                    // 게시글 제목과 링크, 고유 번호(sourceId) 추출
                    Element titleElement = postElement.selectFirst("a.bsubject");
                    if (titleElement == null) {
                        continue;
                    }
                    String link = "https://www.bobaedream.co.kr" + titleElement.attr("href");
                    String title = titleElement.text();

                    Matcher matcher = sourceIdPattern.matcher(link);
                    Long sourceId = null;
                    if (matcher.find()) {
                        sourceId = Long.parseLong(matcher.group(1));
                    } else {
                        System.err.println("경고: 게시글 번호(No)를 찾을 수 없습니다. 건너뜁니다. Link: " + link);
                        continue;
                    }

                    // 작성자 추출
                    Element authorElement = postElement.selectFirst("span.author");
                    String author = Optional.ofNullable(authorElement)
                            .map(Element::text)
                            .orElse(null);

                    // 댓글 수 추출
                    Element commentCountElement = postElement.selectFirst("strong.totreply");
                    int commentCount = Optional.ofNullable(commentCountElement)
                            .map(Element::text)
                            .map(Integer::parseInt)
                            .orElse(0);

                    // 조회수 추출
                    String viewCountStr = postElement.selectFirst("td.count").text().replaceAll(",", "");
                    int viewCount = 0;
                    try {
                        viewCount = Integer.parseInt(viewCountStr);
                    } catch (NumberFormatException e) {
                        System.err.println("조회 수 파싱 오류: " + viewCountStr);
                    }

                    // 추천수 추출
                    String recommendationCountStr = postElement.selectFirst("td.recomm font").text().replaceAll(",", "");
                    int recommendationCount = Integer.parseInt(recommendationCountStr);

                    // 날짜/시간 추출
                    String timeStr = postElement.selectFirst("td.date").text().trim();
                    LocalDateTime createdAt;
                    try {
                        // 시간만 포함된 경우 (예: 13:25)
                        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
                        createdAt = LocalDateTime.of(LocalDate.now(), LocalTime.parse(timeStr, timeFormatter));
                    } catch (DateTimeParseException e) {
                        // 날짜가 포함된 경우 (예: 25.09.22)
                        createdAt = LocalDateTime.of(LocalDate.parse(timeStr, DateTimeFormatter.ofPattern("yy.MM.dd")), LocalTime.now());
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
                            .source("bobaedream")
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