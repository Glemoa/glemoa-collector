package hyunsub.glemoa.collector.common;

import hyunsub.glemoa.collector.repository.PostRepository;
import hyunsub.glemoa.collector.service.ICrawler;
import hyunsub.glemoa.collector.service.impl.*;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import hyunsub.glemoa.collector.entity.Post;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebCollectorScheduler {
    private final List<ICrawler> crawlers;
    private final PostRepository postRepository;

    // 💡 새로 만든 설정 클래스를 주입받습니다.
    private final CrawlerProperties crawlerProperties;

    // 처음 실행인지 확인하는 트리거 변수
    private boolean isInitialRun = true;

    // ✨ 오늘 날짜를 기준으로 목표 날짜 (예: 30일 전.. / 1일 전.. )를 설정합니다.
    LocalDateTime targetDate;
    LocalDateTime initialCrawlStartDate = LocalDateTime.now().minusDays(1);
    LocalDateTime scheduledCrawlInterval;

    Random random = new Random();
    long randomDelayMillis = 0; // 무작위 지연 변수

    // 예시: 매 10분마다 크롤링을 실행
    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.SECONDS)
    public void runCrawlingJob() {
        // 데이터베이스가 비어있는지 확인하여 최초 실행 여부 결정
        if (isInitialRun && postRepository.count() > 0) {
            isInitialRun = false;
        }

        scheduledCrawlInterval = LocalDateTime.now().minusMinutes(30);

        // 처음 실행한 상태라면 1일 전, 아니면 30분 전
        targetDate = isInitialRun ? initialCrawlStartDate : scheduledCrawlInterval;

//        int randomDelayMillis = random.nextInt(119000) + 1000; // 1초에서 120초 사이의 무작위 지연
//        int randomDelayMillis = random.nextInt(1) + 1000; // 1초에서 120초 사이의 무작위 지연
//        try {
//            log.info("다음 크롤링까지 " + (double)randomDelayMillis/1000 + "초 대기합니다.");
//            Thread.sleep(randomDelayMillis);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }

        log.info("스케줄링된 크롤링 작업을 시작합니다.");

        // 여러 스레드를 사용하여 병렬로 크롤링을 실행할 수 있습니다.
        List<List<Post>> allPosts = crawlers.parallelStream()
                .map(crawler -> {
                    try {
                        // 크롤러 실행 전 무작위 지연 시간 추가 (1~3초)
                        Thread.sleep(random.nextInt(2000) + 1000);
                        log.info(crawler.getClass().getSimpleName() + " 크롤러를 " + targetDate + "가 있는 페이지까지 실행합니다.");

                        // 모든 크롤러는 ICrawler 인터페이스를 구현하므로, 별도 캐스팅 없이 호출 가능
                        return crawler.crawl(targetDate);

                    } catch (Exception e) {
                        // 특정 크롤러에서 오류가 발생해도 다른 크롤러에 영향 주지 않도록 예외 처리
                        log.error(crawler.getClass().getSimpleName() + " 크롤링 중 오류가 발생했습니다: " + e.getMessage(), e);
                        // 빈 리스트를 반환하여 스트림이 계속 진행되게 함
                        return new ArrayList<Post>();
                    }
                })
                .collect(Collectors.toList());

        // 크롤링된 모든 데이터를 통합하여 처리합니다.
        log.info("모든 크롤링 작업이 완료되었습니다.");
        System.out.println(allPosts);

        allPosts.forEach(posts -> {
                if (!posts.isEmpty()) {
                    log.info(posts.getFirst().getSource() + " 수집된 게시글 수: " + posts.size());

                    // posts(List<Post>)를 순회하는 두 번째 루프
                    posts.forEach(post -> {
                        log.info(post.toString());
                    });
                }
            });

        // TODO: 수집된 데이터를 데이터베이스에 저장하거나 웹 사이트에 노출하는 로직을 추가합니다.
        // 모든 List<Post>를 하나의 List<Post>로 통합
        List<Post> allCollectedPosts = allPosts.stream()
                .flatMap(List::stream) // List<List<Post>>를 List<Post>로 변환
                .collect(Collectors.toList());

        // 통합된 리스트가 비어있지 않은 경우에만 저장
        if (!allCollectedPosts.isEmpty()) {
            log.info("총 " + allCollectedPosts.size() + "개의 게시글을 데이터베이스에 저장합니다.");
            postRepository.saveAll(allCollectedPosts);
            log.info("총 " + allCollectedPosts.size() + "개의 게시글을 데이터베이스에 저장했습니다.");
            log.info("저장 완료!");
        }
    }
}