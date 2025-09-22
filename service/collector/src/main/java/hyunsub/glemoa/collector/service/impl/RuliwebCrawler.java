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

//@Component
public class RuliwebCrawler implements ICrawler {

    private final String url = "https://bbs.ruliweb.com/best/all/now?page=1";
    private final Pattern boardAndNoPattern = Pattern.compile("/(best|market)/board/(\\d+)/read/(\\d+)");

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

            // 일반 게시글과 베스트 게시글 모두 포함
            Elements postElements = doc.select("table.board_list_table tbody tr.table_body");
//            System.out.println(postElements);

            System.out.println("Ruliweb 크롤링 결과: " + postElements.size());

            for (Element postElement : postElements) {
                try {
                    // 게시글 제목, 링크, 게시글 번호(sourceId) 추출
                    Element subjectLinkElement = postElement.selectFirst("a.subject_link");
                    if (subjectLinkElement == null) {
                        continue;
                    }
                    String title = subjectLinkElement.select(".text_over").text();
                    String link = "https://bbs.ruliweb.com" + subjectLinkElement.attr("href");

                    // 링크에서 게시판 번호와 게시글 번호 추출
                    Matcher matcher = boardAndNoPattern.matcher(link);
                    Long sourceId = null;
                    if (matcher.find()) {
                        sourceId = Long.parseLong(matcher.group(3));
                    } else {
                        System.err.println("경고: 링크에서 게시글 번호(sourceId)를 찾을 수 없습니다. 건너뜁니다. Link: " + link);
                        continue;
                    }

                    // 작성자 추출
                    String author = postElement.selectFirst("td.writer").text().trim();

                    // 추천수 추출
                    String recommendationCountStr = postElement.selectFirst("td.recomd").text().trim();
                    int recommendationCount = Integer.parseInt(recommendationCountStr);

                    // 조회수 추출
                    String viewCountStr = postElement.selectFirst("td.hit").text().trim();
                    int viewCount = Integer.parseInt(viewCountStr);

                    // 댓글 수 추출
                    Element commentCountElement = postElement.selectFirst("span.num_reply");
                    int commentCount = Optional.ofNullable(commentCountElement)
                            .map(Element::text)
                            .map(s -> s.replaceAll("[^0-9]", ""))
                            .filter(s -> !s.isEmpty())
                            .map(Integer::parseInt)
                            .orElse(0);

                    // 날짜/시간 추출
                    String timeStr = postElement.selectFirst("td.time").text().trim();
                    LocalDateTime createdAt;
                    try {
                        // 날짜가 포함된 경우를 대비 (ex: 24.12.06)
                        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yy.MM.dd");
                        createdAt = LocalDate.parse(timeStr, dateFormatter).atStartOfDay();
                    } catch (DateTimeParseException e) {
                        // 시간만 포함된 경우 (ex: 15:18)
                        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
                        createdAt = LocalDateTime.of(LocalDate.now(), LocalTime.parse(timeStr, timeFormatter));
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
                            .source("ruliweb")
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