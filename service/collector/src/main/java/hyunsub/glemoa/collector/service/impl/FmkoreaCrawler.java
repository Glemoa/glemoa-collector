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
import java.util.ArrayList;
import java.util.List;

@Component
public class FmkoreaCrawler implements ICrawler {

    private final String url = "https://www.fmkorea.com/index.php?mid=best&listStyle=list&page=1";

    @Override
    public List<Post> crawl() {
        List<Post> posts = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Referer", "https://www.fmkorea.com/")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Connection", "keep-alive")
                    .get();

//            Elements postElements = doc.select(".bd_lst_tb tbody tr:not(.notice)");
            Elements postElements = doc.select("table.bd_lst tbody tr:not(.notice)");

            System.out.println("Fmkorea 크롤링 결과: " +postElements.size());

            for (Element postElement : postElements) {
                // 게시글 번호 (sourceId) 추출
                String linkHref = postElement.selectFirst("a.hx").attr("href");
                String sourceIdElement  = linkHref.replaceAll(".*document_srl=(\\d+).*", "$1");
                Long sourceId = Long.parseLong(sourceIdElement);

                // 제목, 링크 추출
                String title = postElement.selectFirst("a.hx").text();
                String link = "https://www.fmkorea.com" + linkHref;

                // 작성자, 추천수, 조회수, 댓글 수 추출
                String author = postElement.selectFirst("td.author a").text();
                String recommendationCountStr = postElement.selectFirst("td.m_no.m_no_voted").text();
                String viewCountStr = postElement.select("td.m_no").last().text();
                String commentCountStr = postElement.selectFirst("a.replyNum").text();

                // 날짜/시간 추출
                String timeStr = postElement.selectFirst("td.time").text();
                LocalDateTime createdAt;
                if (timeStr.contains(".")) { // 날짜가 포함된 경우 (ex: 23.09.20)
                    String[] dateParts = timeStr.split("\\.");
                    int year = 2000 + Integer.parseInt(dateParts[0]);
                    int month = Integer.parseInt(dateParts[1]);
                    int day = Integer.parseInt(dateParts[2]);
                    createdAt = LocalDateTime.of(year, month, day, 0, 0);
                } else { // 시간만 포함된 경우 (ex: 19:49)
                    createdAt = LocalDateTime.of(LocalDate.now(), LocalTime.parse(timeStr));
                }

                Post post = Post.builder()
                        .sourceId(sourceId)
                        .title(title)
                        .link(link)
                        .author(author)
                        .commentCount(Integer.parseInt(commentCountStr))
                        .viewCount(Integer.parseInt(viewCountStr.replaceAll(",", "")))
                        .recommendationCount(Integer.parseInt(recommendationCountStr.replaceAll(",", "")))
                        .createdAt(createdAt)
                        .source("fmkorea")
                        .build();

                posts.add(post);
//                System.out.println(post.toString());
            }

        } catch (IOException e) {
            System.err.println("크롤링 중 오류가 발생했습니다: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("데이터 추출 중 오류가 발생했습니다: " + e.getMessage());
            e.printStackTrace();
        }
        return posts;
    }
}