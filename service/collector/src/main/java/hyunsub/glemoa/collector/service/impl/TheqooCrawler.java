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

@Component
public class TheqooCrawler implements ICrawler {

    private final String url = "https://theqoo.net/hot?page=1";

    @Override
    public List<Post> crawl() {
        List<Post> posts = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .get();

            // 공지사항을 제외한 일반 게시글 목록 선택
            // notice 클래스와 notice_expand 클래스 두 가지 모두를 제외
            Elements postElements = doc.select("table.theqoo_board_table tbody tr:not(.notice):not(.notice_expand)");

            System.out.println("Theqoo 크롤링 결과: " + postElements.size());
//            System.out.println(postElements);

            for (Element postElement : postElements) {
                try {
                    // postElement의 모든 td 요소를 순서대로 가져옴
                    Elements tdElements = postElement.select("td");

//                    if (tdElements.size() < 5) {
//                        System.err.println("경고: 예상치 못한 열 개수입니다. 이 행은 건너뜁니다.");
//                        continue;
//                    }

                    // sourceId (게시글 고유 번호) 추출
                    String sourceIdStr = tdElements.get(0).text().trim();
                    Long sourceId = null;
                    if (!sourceIdStr.isEmpty()) {
                        try {
                            sourceId = Long.parseLong(sourceIdStr);
                        } catch (NumberFormatException e) {
                            System.err.println("게시글 번호 파싱 오류: " + sourceIdStr + ". 이 행은 건너뜁니다.");
                            continue;
                        }
                    }

                    // 제목, 링크 추출
                    Element titleElement = tdElements.get(2).selectFirst("a");
                    if (titleElement == null) {
                        System.err.println("경고: 제목 엘리먼트가 없는 게시글이 있습니다. 건너뜁니다.");
                        continue;
                    }
                    String title = titleElement.text();
                    String link = "https://theqoo.net" + titleElement.attr("href");

                    // 댓글 수 추출 (엘리먼트가 없을 경우 0으로 설정)
                    Element commentCountElement = tdElements.get(2).selectFirst("a.replyNum");
                    int commentCount = Optional.ofNullable(commentCountElement)
                            .map(Element::text)
                            .map(Integer::parseInt)
                            .orElse(0);

                    // 날짜/시간 추출
                    Element timeElement = tdElements.get(3);
                    LocalDateTime createdAt = null;
                    if (timeElement != null) {
                        String timeStr = timeElement.text().trim();
                        try {
                            // 날짜가 포함된 경우 (예: 24.12.06)
                            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yy.MM.dd");
                            createdAt = LocalDate.parse(timeStr, dateFormatter).atStartOfDay();
                        } catch (DateTimeParseException e) {
                            // 시간만 포함된 경우 (예: 19:54)
                            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
                            createdAt = LocalDateTime.of(LocalDate.now(), LocalTime.parse(timeStr, timeFormatter));
                        }
                    }

                    // 조회 수 추출
                    String viewCountStr = tdElements.get(4).text().replaceAll(",", "");
                    int viewCount = 0;
                    try {
                        viewCount = Integer.parseInt(viewCountStr);
                    } catch (NumberFormatException e) {
                        System.err.println("조회 수 파싱 오류: " + viewCountStr);
                    }

                    Post post = Post.builder()
                            .sourceId(sourceId)
                            .title(title)
                            .link(link)
                            .author(null)
                            .commentCount(commentCount)
                            .viewCount(viewCount)
                            .recommendationCount(null)
                            .createdAt(createdAt)
                            .source("theqoo")
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