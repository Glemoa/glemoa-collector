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

//@Component
public class MlbparkCrawler implements ICrawler {

    private final String url = "https://mlbpark.donga.com/mp/b.php?p=1&m=list&b=bullpen&query=&select=&subquery=&subselect=&user=";

    @Override
    public List<Post> crawl() {
        List<Post> posts = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .get();

            Elements postElements = doc.select("table.tbl_type01 tbody tr");

            System.out.println("Mlbpark 크롤링 결과: " + postElements.size());

            for (Element postElement : postElements) {
                try {
                    Element firstTd = postElement.selectFirst("td");
                    if (firstTd != null && "공지".equals(firstTd.text().trim())) {
                        continue; // 공지사항 게시글은 건너뛰기
                    }

                    // 게시글 번호(sourceId) 추출
                    String sourceIdStr = Optional.ofNullable(firstTd).map(Element::text).orElse("");
                    Long sourceId = null;
                    if (!sourceIdStr.isEmpty()) {
                        try {
                            sourceId = Long.parseLong(sourceIdStr);
                        } catch (NumberFormatException e) {
                            System.err.println("경고: 게시글 번호 파싱 오류. 건너뜁니다.");
                            continue;
                        }
                    }

                    // 제목, 링크 추출
                    Element titleElement = postElement.selectFirst("a.txt");
                    if (titleElement == null) {
                        continue;
                    }
                    String title = titleElement.text().trim();
                    String link = "https://mlbpark.donga.com" + titleElement.attr("href");

                    // 작성자 추출
                    Element authorElement = postElement.selectFirst("span.nick");
                    String author = Optional.ofNullable(authorElement).map(Element::text).orElse("익명");

                    // 댓글 수 추출
                    Element commentCountElement = postElement.selectFirst("span.replycnt");
                    int commentCount = Optional.ofNullable(commentCountElement)
                            .map(Element::text)
                            .map(s -> s.replaceAll("[^0-9]", ""))
                            .filter(s -> !s.isEmpty())
                            .map(Integer::parseInt)
                            .orElse(0);

                    // 조회 수 추출
                    String viewCountStr = postElement.selectFirst("span.viewV").text().trim().replaceAll(",", "");
                    int viewCount = 0;
                    try {
                        viewCount = Integer.parseInt(viewCountStr);
                    } catch (NumberFormatException e) {
                        System.err.println("경고: 조회수 파싱 오류: " + viewCountStr);
                    }

                    // 날짜/시간 추출
                    String timeStr = postElement.selectFirst("span.date").text().trim();
                    LocalDateTime createdAt;
                    try {
                        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
                        createdAt = LocalDateTime.of(LocalDate.now(), LocalTime.parse(timeStr, timeFormatter));
                    } catch (DateTimeParseException e) {
                        // 날짜가 포함된 경우 (ex: 2025-09-15)
                        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                        createdAt = LocalDate.parse(timeStr, dateFormatter).atStartOfDay();
                    }

                    Post post = Post.builder()
                            .sourceId(sourceId)
                            .title(title)
                            .link(link)
                            .author(author)
                            .commentCount(commentCount)
                            .viewCount(viewCount)
                            .recommendationCount(null) // 추천수 정보는 HTML에 없어 null로 처리
                            .createdAt(createdAt)
                            .source("mlbpark")
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