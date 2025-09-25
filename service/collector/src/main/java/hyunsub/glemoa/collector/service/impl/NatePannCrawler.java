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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class NatePannCrawler implements ICrawler {

//    private final String baseUrl = "https://pann.nate.com/talk/ranking?rankingType=total&page=%d";
//    private final String basePannUrl = "https://pann.nate.com";
//    private final Pattern articleNoPattern = Pattern.compile("/talk/(\\d+)");
//
////    @Override
////    public List<Post> crawl() {
////        return crawl(1);
////    }
//
//    @Override
//    public List<Post> crawl(LocalDateTime until) {
//        List<Post> posts = new ArrayList<>();
//        int page = 1;
//        boolean continueCrawling = true;
//
////      for (int page = 1; page <= pageCount; page++) {
//        while (continueCrawling) {
//            // --- 페이지 요청 간 무작위 지연 시간 추가 ---
//            try {
//                int randomDelay = (int) (Math.random() * 2000) + 1000; // 1초~3초 사이 지연
//                double delaySeconds = randomDelay / 1000.0;
//                log.info("페이지 요청 간 무작위 지연 시간 : " + delaySeconds + "ms");
//                Thread.sleep(randomDelay);
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//            }
//            // ----------------------------------------------
//
//            String url = String.format(baseUrl, page);
//            try {
//                Document doc = Jsoup.connect(url)
//                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
//                        .get();
//
//                Elements postElements = doc.select("ul.post_wrap li");
//
//                log.info("NatePann 크롤링 결과: " + postElements.size());
//
//                for (Element postElement : postElements) {
//                    try {
//                        // 제목, 링크 추출
//                        Element titleLinkElement = postElement.selectFirst("dl dt h2 a");
//                        if (titleLinkElement == null) {
//                            continue;
//                        }
//                        String title = titleLinkElement.text().trim();
//                        String link = basePannUrl + titleLinkElement.attr("href");
//
//                        // 게시글 고유 번호(sourceId) 추출
//                        Matcher matcher = articleNoPattern.matcher(link);
//                        Long sourceId = null;
//                        if (matcher.find()) {
//                            sourceId = Long.parseLong(matcher.group(1));
//                        } else {
//                            log.warn("경고: 링크에서 게시글 번호(sourceId)를 찾을 수 없습니다. 건너뜁니다. Link: " + link);
//                            continue;
//                        }
//
//                        // 댓글 수 추출
//                        Element commentCountElement = postElement.selectFirst("dt span.reple-num");
//                        int commentCount = Optional.ofNullable(commentCountElement)
//                                .map(Element::text)
//                                .map(s -> s.replaceAll("[^0-9]", ""))
//                                .filter(s -> !s.isEmpty())
//                                .map(Integer::parseInt)
//                                .orElse(0);
//
//                        // 조회 수, 추천 수 추출
//                        String viewCountStr = postElement.selectFirst("dd.info span.count").text().replaceAll("[^0-9]", "");
//                        int viewCount = 0;
//                        try {
//                            viewCount = Integer.parseInt(viewCountStr.trim());
//                        } catch (NumberFormatException e) {
//                            log.warn("경고: 조회수 파싱 오류: " + viewCountStr);
//                        }
//
//                        String recoCountStr = postElement.selectFirst("dd.info span.rcm").text().replaceAll("[^0-9]", "");
//                        int recommendationCount = 0;
//                        try {
//                            recommendationCount = Integer.parseInt(recoCountStr.trim());
//                        } catch (NumberFormatException e) {
//                            log.warn("경고: 추천수 파싱 오류: " + recoCountStr);
//                        }
//
//                        Post post = Post.builder()
//                                .sourceId(sourceId)
//                                .title(title)
//                                .link(link)
//                                .author("익명") // 작성자 정보가 없어 익명으로 처리
//                                .commentCount(commentCount)
//                                .viewCount(viewCount)
//                                .recommendationCount(recommendationCount)
//                                .createdAt(LocalDateTime.now()) // 날짜/시간 정보가 없어 현재 시점으로 처리
//                                .source("pann")
//                                .build();
//
//                        posts.add(post);
////                    System.out.println(post.toString());
//
//                    } catch (Exception e) {
//                        log.warn("개별 게시글 크롤링 중 오류가 발생했습니다: " + e.getMessage());
//                        e.printStackTrace();
//                    }
//                }
//            } catch (IOException e) {
//                log.error("크롤링 중 오류가 발생했습니다: " + e.getMessage());
//                e.printStackTrace();
//            }
//        }
//        return posts;
//    }

private final String todayUrl = "https://pann.nate.com/talk/ranking?rankingType=total&page=%d";
    private final String dailyUrl = "https://pann.nate.com/talk/ranking/d?stdt=%s&page=%d&rankingType=total";
    private final String basePannUrl = "https://pann.nate.com";
    private final Pattern articleNoPattern = Pattern.compile("/talk/(\\d+)");
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final Random random = new Random();

    @Override
    public List<Post> crawl(LocalDateTime until) {
        List<Post> allPosts = new ArrayList<>();
        LocalDate currentDate = LocalDate.now();
        LocalDate untilDate = until.toLocalDate();

        // 현재 날짜부터 지정된 until 날짜까지 하루씩 역순으로 크롤링
        while (!currentDate.isBefore(untilDate)) {
            // 각 날짜별로 3페이지씩 크롤링 (페이지 수는 필요에 따라 조정 가능)
            allPosts.addAll(crawlByDate(currentDate, 2));
            currentDate = currentDate.minusDays(1);
        }
        return allPosts;
    }

    // 날짜와 페이지 수를 받아 크롤링을 수행하는 핵심 메서드
    private List<Post> crawlByDate(LocalDate targetDate, int pageCount) {
        List<Post> posts = new ArrayList<>();
        String targetDateStr = targetDate.format(dateFormatter);
        boolean isToday = targetDate.isEqual(LocalDate.now());

        log.info("NatePann " + (isToday ? "오늘" : targetDateStr) + " 크롤링을 시작합니다.");

        for (int page = 1; page <= pageCount; page++) {
            try {
                int randomDelay = random.nextInt(2000) + 1000;
                double delaySeconds = randomDelay / 1000.0;
                log.info("페이지 요청 간 무작위 지연 시간 : " + delaySeconds + " s");
                Thread.sleep(randomDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return posts;
            }

            String url = isToday ? String.format(todayUrl, page) : String.format(dailyUrl, targetDateStr, page);

            try {
                Document doc = Jsoup.connect(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .get();

                Elements postElements = doc.select("ul.post_wrap li");
                if (postElements.isEmpty()) {
                    log.info(targetDateStr + " 날짜의 " + page + "페이지에서 더 이상 게시글이 없습니다. 크롤링을 종료합니다.");
                    break;
                }

                for (Element postElement : postElements) {
                    try {
                        Element titleLinkElement = postElement.selectFirst("dl dt h2 a");
                        if (titleLinkElement == null) continue;
                        String title = titleLinkElement.text().trim();
                        String link = basePannUrl + titleLinkElement.attr("href");

                        // URL 정규화: '?' 뒤의 쿼리 파라미터를 제거하여 항상 동일한 link를 보장합니다.
                        int queryIndex = link.indexOf('?');
                        if (queryIndex != -1) {
                            link = link.substring(0, queryIndex);
                        }

                        Matcher matcher = articleNoPattern.matcher(link);
                        Long sourceId = null;
                        if (matcher.find()) {
                            sourceId = Long.parseLong(matcher.group(1));
                        } else {
                            log.warn("경고: 링크에서 게시글 번호(sourceId)를 찾을 수 없습니다. 건너뜁니다. Link: " + link);
                            continue;
                        }

                        Element commentCountElement = postElement.selectFirst("dt span.reple-num");
                        int commentCount = Optional.ofNullable(commentCountElement)
                                .map(Element::text)
                                .map(s -> s.replaceAll("[^0-9]", ""))
                                .filter(s -> !s.isEmpty())
                                .map(Integer::parseInt)
                                .orElse(0);

                        String viewCountStr = postElement.selectFirst("dd.info span.count").text().replaceAll("[^0-9]", "");
                        int viewCount = 0;
                        try {
                            viewCount = Integer.parseInt(viewCountStr.trim());
                        } catch (NumberFormatException e) {
                            log.warn("경고: 조회수 파싱 오류: " + viewCountStr);
                        }

                        String recoCountStr = postElement.selectFirst("dd.info span.rcm").text().replaceAll("[^0-9]", "");
                        int recommendationCount = 0;
                        try {
                            recommendationCount = Integer.parseInt(recoCountStr.trim());
                        } catch (NumberFormatException e) {
                            log.warn("경고: 추천수 파싱 오류: " + recoCountStr);
                        }

                        Post post = Post.builder()
                                .sourceId(sourceId)
                                .title(title)
                                .link(link)
                                .author("익명")
                                .commentCount(commentCount)
                                .viewCount(viewCount)
                                .recommendationCount(recommendationCount)
                                .createdAt(targetDate.atStartOfDay())
                                .source("natepann")
                                .build();
                        posts.add(post);
                    } catch (Exception e) {
                        log.warn("개별 게시글 크롤링 중 오류가 발생했습니다: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            } catch (IOException e) {
                log.error("크롤링 중 오류가 발생했습니다: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return posts;
    }
}