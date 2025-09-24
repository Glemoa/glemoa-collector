package hyunsub.glemoa.collector.service.impl;

import hyunsub.glemoa.collector.entity.Post;
import hyunsub.glemoa.collector.service.ICrawler;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class BobaedreamCrawler implements ICrawler {

//    private final String url = "https://www.bobaedream.co.kr/board/bulletin/list.php?code=best&s_cate=&maker_no=&model_no=&or_gu=10&or_se=desc&s_selday=&pagescale=70&info3=&noticeShow=&s_select=Subject&s_key=&level_no=&bestCode=&bestDays=&bestbbs=&vdate=&type=list&page=1";
    private final String baseUrl = "https://www.bobaedream.co.kr/board/bulletin/list.php?code=best&s_cate=&maker_no=&model_no=&or_gu=10&or_se=desc&s_selday=&pagescale=70&info3=&noticeShow=&s_select=Subject&s_key=&level_no=&bestCode=&bestDays=&bestbbs=&vdate=&type=list&page=%d";
    private final Pattern sourceIdPattern = Pattern.compile("No=(\\d+)");

//    @Override
//    public List<Post> crawl() {
//        return crawl(1);
//    }

    @Override
    public List<Post> crawl(LocalDateTime until) {
        List<Post> posts = new ArrayList<>();
        int page = 1;
        boolean continueCrawling = true;

//      for (int page = 1; page <= pageCount; page++) {
        while (continueCrawling) {

            // --- 페이지 요청 간 무작위 지연 시간 추가 ---
            try {
                int randomDelay = (int) (Math.random() * 2000) + 1000; // 1초~3초 사이 지연
                double delaySeconds = randomDelay / 1000.0;
                log.info("페이지 요청 간 무작위 지연 시간 : " + delaySeconds + "ms");
                Thread.sleep(randomDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // ----------------------------------------------

            String url = String.format(baseUrl, page);
            try {
                Document doc = Jsoup.connect(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .get();

                Elements postElements = doc.select("table#boardlist tbody tr[itemscope]");

                log.info("Bobaedream " + page + "페이지 크롤링 결과: " + postElements.size());
//                log.info("Bobaedream 크롤링 결과: " + postElements.size());

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
                            log.warn("경고: 게시글 번호(No)를 찾을 수 없습니다. 건너뜁니다. Link: " + link);
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
                        } catch (DateTimeParseException timeEx) {
                            try {
                                // 날짜가 '연도.월.일' 형식인 경우 (예: 25.09.22)
                                DateTimeFormatter dateFormatter1 = DateTimeFormatter.ofPattern("yy.MM.dd");
                                createdAt = LocalDate.parse(timeStr, dateFormatter1).atStartOfDay();
                            } catch (DateTimeParseException dateEx1) {
                                // 날짜가 '월/일' 형식인 경우 (예: 09/17)
                                DateTimeFormatter dateFormatter2 = DateTimeFormatter.ofPattern("MM/dd");
                                MonthDay monthDay = MonthDay.parse(timeStr, dateFormatter2);
                                LocalDate localDate = monthDay.atYear(LocalDate.now().getYear());
                                // 현재 연도를 붙여서 완전한 날짜를 만듭니다.
                                createdAt = localDate.atStartOfDay();
                            }
                        }

                        // ✨ 게시글 날짜가 목표 날짜보다 이전이면 중단
                        if (createdAt.isBefore(until)) {
                            continueCrawling = false;
                            break;
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
                        log.warn("개별 게시글 크롤링 중 오류가 발생했습니다: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                log.warn("크롤링 중 오류가 발생했습니다: " + e.getMessage());
                e.printStackTrace();
            }
            if (continueCrawling) {
                page++;
            }
        }
        return posts;
    }
}