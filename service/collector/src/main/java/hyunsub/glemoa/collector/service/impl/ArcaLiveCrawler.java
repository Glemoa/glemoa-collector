package hyunsub.glemoa.collector.service.impl;

import hyunsub.glemoa.collector.entity.Post;
import hyunsub.glemoa.collector.service.ICrawler;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ArcaLiveCrawler implements ICrawler {

//    private final String url = "https://arca.live/b/live?p=1";
    private final String baseUrl = "https://arca.live/b/live?p=%d";
    private final Pattern articleNoPattern = Pattern.compile("/b/live/(\\d+)");

//    @Override
//    public List<Post> crawl() {
//        return crawl(1);
//    }

    @Override
    public List<Post> crawl(LocalDateTime until) {
        List<Post> posts = new ArrayList<>();
        int page = 1;
        boolean continueCrawling = true;

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("user-agent=live.arca.android/1.0.0");

        WebDriver driver = new ChromeDriver(options);

        try {
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
                driver.get(url);

                // 공지사항을 제외한 일반 게시글 목록 선택
                List<WebElement> postElements = driver.findElements(By.cssSelector("div.vrow.hybrid:not(.notice)"));

                log.info("ArcaLive " + page + "페이지 크롤링 결과: " + postElements.size());

                for (WebElement postElement : postElements) {
                    try {
                        // 제목, 링크, 게시글 번호(sourceId) 추출
                        WebElement titleElement = postElement.findElement(By.cssSelector("a.title.hybrid-title"));
                        String title = titleElement.getText().trim();
                        String link = titleElement.getAttribute("href");

                        Matcher matcher = articleNoPattern.matcher(link);
                        Long sourceId = null;
                        if (matcher.find()) {
                            sourceId = Long.parseLong(matcher.group(1));
                        } else {
                            log.warn("경고: 링크에서 게시글 번호(sourceId)를 찾을 수 없습니다. 건너뜁니다. Link: " + link);
                            continue;
                        }

                        // 작성자 추출
                        String author;
                        try {
                            author = postElement.findElement(By.cssSelector("span.vcol.col-author span.user-info span[data-filter]")).getText();
                        } catch (Exception e) {
                            author = "익명";
                        }

                        // 댓글 수 추출
                        int commentCount = 0;
                        try {
                            String commentCountStr = postElement.findElement(By.cssSelector("span.comment-count")).getText().replaceAll("[^0-9]", "");
                            if (!commentCountStr.isEmpty()) {
                                commentCount = Integer.parseInt(commentCountStr);
                            }
                        } catch (Exception e) {
                            // 댓글이 없으면 0으로 유지
                        }

                        // 조회 수, 추천 수 추출
                        String viewCountStr = postElement.findElement(By.cssSelector("span.vcol.col-view")).getText().trim().replaceAll(",", "");
                        int viewCount = 0;
                        try {
                            viewCount = Integer.parseInt(viewCountStr);
                        } catch (NumberFormatException e) {
                            log.warn("경고: 조회수 파싱 오류: " + viewCountStr);
                        }

                        String recoCountStr = postElement.findElement(By.cssSelector("span.vcol.col-rate")).getText().trim().replaceAll(",", "");
                        int recommendationCount = 0;
                        try {
                            recommendationCount = Integer.parseInt(recoCountStr);
                        } catch (NumberFormatException e) {
                            log.warn("경고: 추천수 파싱 오류: " + recoCountStr);
                        }

                        // 작성 시간 추출 (datetime 속성을 우선적으로 사용)
                        LocalDateTime createdAt;
//                        try {
//                            String dateTimeAttr = postElement.findElement(By.cssSelector("time[datetime]")).getAttribute("datetime");
//                            createdAt = ZonedDateTime.parse(dateTimeAttr).toLocalDateTime();
//                        } catch (Exception dateEx) {
                            // datetime 속성이 없거나 파싱 실패 시, 태그의 텍스트를 파싱
                            String timeStr = postElement.findElement(By.cssSelector("span.vcol.col-time")).getText().trim();
                            if (timeStr.contains("시간전")) {
                                int hoursAgo = Integer.parseInt(timeStr.replaceAll("[^0-9]", ""));
                                createdAt = LocalDateTime.now().minusHours(hoursAgo);
                            } else if (timeStr.contains("분전")) {
                                int minutesAgo = Integer.parseInt(timeStr.replaceAll("[^0-9]", ""));
                                createdAt = LocalDateTime.now().minusMinutes(minutesAgo);
                            } else  { // MM-dd 형식 처리
                                String dateTimeAttr = postElement.findElement(By.cssSelector("time[datetime]")).getAttribute("datetime");
                                createdAt = ZonedDateTime.parse(dateTimeAttr).toLocalDateTime();
//                                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
//                                LocalDate localDate = LocalDate.parse(timeStr, formatter);
//                                createdAt = localDate.atTime(LocalTime.now());
//                                DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM-dd");
//                                // Use MonthDay to parse just the month and day
//                                MonthDay monthDay = MonthDay.parse(timeStr, dateFormatter);
//                                // Apply the current year
//                                LocalDate postDate = monthDay.atYear(LocalDate.now().getYear());
//
//                                // If the parsed date is in the future, it must be from last year
//                                if (postDate.isAfter(LocalDate.now())) {
//                                    postDate = postDate.minusYears(1);
//                                }
//                                createdAt = postDate.atStartOfDay();
                            }
//                            else {
//                                // 다른 형식의 날짜가 나타나면 현재 시간으로 처리
//                                createdAt = LocalDateTime.now();
//                            }
//                        }

                        // ✨ 게시글 날짜가 목표 날짜보다 이전이면 중단
                        if (createdAt.isBefore(until) || page > 100) {
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
                                .source("arcalive")
                                .build();

                        posts.add(post);

                    } catch (Exception e) {
                        log.warn("개별 게시글 크롤링 중 오류가 발생했습니다: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                if (continueCrawling) {
                    page++;
                }
            }
        } catch (Exception e) {
            log.warn("크롤링 중 오류가 발생했습니다: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
        return posts;
    }
}