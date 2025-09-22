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
public class InvenCrawler implements ICrawler {

    private final String url = "https://www.inven.co.kr/board/webzine/2097?p=1";
    private final Pattern articleNoPattern = Pattern.compile("/board/webzine/2097/(\\d+)");

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

            // 공지사항을 제외한 일반 게시글 목록 선택
            Elements postElements = doc.select("table.thumbnail tbody tr:not(.notice)");

            System.out.println("Inven 크롤링 결과: " + postElements.size());

            for (Element postElement : postElements) {
                try {
                    // 제목, 링크 추출
                    Element subjectLinkElement = postElement.selectFirst("a.subject-link");
                    if (subjectLinkElement == null) {
                        continue;
                    }
                    String title = subjectLinkElement.text().trim();
                    String link = subjectLinkElement.attr("href");

                    // 게시글 고유 번호(sourceId) 추출
                    Matcher matcher = articleNoPattern.matcher(link);
                    Long sourceId = null;
                    if (matcher.find()) {
                        sourceId = Long.parseLong(matcher.group(1));
                    } else {
                        System.err.println("경고: 링크에서 게시글 번호(sourceId)를 찾을 수 없습니다. 건너뜁니다. Link: " + link);
                        continue;
                    }

                    // 작성자 추출
                    Element userElement = postElement.selectFirst("td.user span.layerNickName");
                    String author = Optional.ofNullable(userElement)
                            .map(Element::text)
                            .orElse("익명");

                    // 댓글 수 추출
                    Element commentCountElement = postElement.selectFirst("span.con-comment");
                    int commentCount = Optional.ofNullable(commentCountElement)
                            .map(Element::text)
                            .map(s -> s.replaceAll("[^0-9]", ""))
                            .filter(s -> !s.isEmpty())
                            .map(Integer::parseInt)
                            .orElse(0);

                    // 조회 수, 추천 수 추출
                    String viewCountStr = postElement.selectFirst("td.view").text().replaceAll(",", "");
                    int viewCount = Integer.parseInt(viewCountStr.trim());

                    String recoCountStr = postElement.selectFirst("td.reco").text().replaceAll(",", "");
                    int recommendationCount = Integer.parseInt(recoCountStr.trim());

                    // 날짜/시간 추출
                    String timeStr = postElement.selectFirst("td.date").text().trim();
                    LocalDateTime createdAt;
                    try {
                        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
                        createdAt = LocalDateTime.of(LocalDate.now(), LocalTime.parse(timeStr, timeFormatter));
                    } catch (DateTimeParseException e) {
                        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM-dd");
                        createdAt = LocalDate.parse(timeStr, dateFormatter).atStartOfDay();
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
                            .source("inven")
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