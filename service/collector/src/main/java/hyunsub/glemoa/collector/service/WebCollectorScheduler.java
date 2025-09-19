package hyunsub.glemoa.collector.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import hyunsub.glemoa.collector.entity.dc;

public class WebCollector {
    public static void main(String[] args) {
        // 크롤링할 대상 URL
        String url = "https://gall.dcinside.com/board/lists/?id=dcbest&page=1&_dcbest=6";

        // 크롤링 결과를 저장할 리스트
        List<dc> posts = new ArrayList<>();

        // 날짜/시간 형식을 정의합니다. (예: 2025-09-19 20:55:01)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try {
            // 1. URL에 접속하여 HTML 문서 가져오기 (User-Agent 설정)
            Document doc = Jsoup.connect(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .get();

            // 2. 게시글 목록을 담고 있는 HTML 요소 선택하기
            // '공지', '설문'을 제외하고 일반 게시글만 선택하기 위해 .us-post 클래스를 사용합니다.
            Elements postElements = doc.select("tr.ub-content.us-post");

            // 3. 각 게시글에서 데이터 추출하여 객체로 만들기
            for (Element postElement : postElements) {
                // 게시글 번호 추출
                String idText = postElement.selectFirst("td.gall_num").text();
                Long id = Long.parseLong(idText);

                // 제목과 링크 추출
                Element titleElement = postElement.selectFirst("td.gall_tit.ub-word a");
                String title = titleElement.text();
                // 링크는 상대 경로이므로 abs:href를 사용해 절대 경로로 변환합니다.
                String link = titleElement.attr("abs:href");

                // 작성자 추출 (닉네임이나 IP를 가져옵니다.)
                String author = postElement.selectFirst("td.gall_writer.ub-writer").attr("data-nick");

                // 숫자 데이터 추출 (문자열에서 숫자만 남기기)
                String commentsStr = postElement.selectFirst("a.reply_numbox").text();
                String viewsStr = postElement.selectFirst("td.gall_count").text();
                String upvotesStr = postElement.selectFirst("td.gall_recommend").text();

                Integer commentCount = Integer.parseInt(commentsStr.replaceAll("[^0-9]", ""));
                Integer viewCount = Integer.parseInt(viewsStr.replaceAll("[^0-9]", ""));
                Integer upvoteCount = Integer.parseInt(upvotesStr.replaceAll("[^0-9]", ""));

                // 작성 시간 추출 및 포맷팅
                String dateString = postElement.selectFirst("td.gall_date").attr("title");
                LocalDateTime createdAt = LocalDateTime.parse(dateString, formatter);

                // 엔터티 객체 생성
                dc post = dc.builder()
                        .id(id)
                        .title(title)
                        .link(link)
                        .author(author)
                        .commentCount(commentCount)
                        .viewCount(viewCount)
                        .upvoteCount(upvoteCount)
                        .createdAt(createdAt)
                        .build();

                posts.add(post);
            }

            // 크롤링된 데이터 확인
            System.out.println("총 " + posts.size() + "개의 게시글을 크롤링했습니다.");
            posts.forEach(System.out::println);

        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("데이터 추출 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}