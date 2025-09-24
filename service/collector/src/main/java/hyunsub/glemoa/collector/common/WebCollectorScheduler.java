package hyunsub.glemoa.collector.common;

import hyunsub.glemoa.collector.repository.PostRepository;
import hyunsub.glemoa.collector.service.ICrawler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import hyunsub.glemoa.collector.entity.Post;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebCollectorScheduler {
    private final List<ICrawler> crawlers;
    private final PostRepository postRepository;

    @Value("${glemoa.scheduler.initial-crawl-days:1}")
    private int initialCrawlDays;

    @Value("${glemoa.scheduler.scheduled-crawl-minutes:30}")
    private int scheduledCrawlMinutes;

    Random random = new Random();
    long randomDelayMillis = 0; // 무작위 지연 변수

    // 스케줄러가 끝나고 N초 뒤에 다시 스케줄러 시작.
    @Scheduled(fixedDelayString = "${glemoa.scheduler.fixed-delay-seconds:60}000")
    public void runCrawlingJob() {
        log.info("스케줄링된 크롤링 작업을 시작합니다.");

        // 여러 스레드를 사용하여 병렬로 크롤링을 실행할 수 있습니다.
        List<List<Post>> allPosts = crawlers.parallelStream()
                .map(crawler -> {
                    String source = crawler.getClass().getSimpleName().replace("Crawler", "").toLowerCase();
                    try {
                        // 크롤러별로 마지막 수집 시간을 DB에서 조회
                        LocalDateTime targetDate = postRepository.findTopBySourceOrderByCreatedAtDesc(source)
                                .map(Post::getCreatedAt)
                                .orElse(LocalDateTime.now().minusDays(initialCrawlDays));

                        // 크롤러 실행 전 무작위 지연 시간 추가 (1~3초)
                        Thread.sleep(random.nextInt(2000) + 1000);
                        log.info("[{}] 크롤러를 실행합니다. 목표 날짜: {}", source, targetDate);

                        // 모든 크롤러는 ICrawler 인터페이스를 구현하므로, 별도 캐스팅 없이 호출 가능
                        return crawler.crawl(targetDate);

                    } catch (Exception e) {
                        // 특정 크롤러에서 오류가 발생해도 다른 크롤러에 영향 주지 않도록 예외 처리
                        log.error("[{}] 크롤링 중 오류가 발생했습니다: {}", source, e.getMessage(), e);
                        // 빈 리스트를 반환하여 스트림이 계속 진행되게 함
                        return new ArrayList<Post>();
                    }
                })
                .collect(Collectors.toList());

        // 크롤링된 모든 데이터를 통합하여 처리합니다.
        log.info("모든 크롤링 작업이 완료되었습니다.");
//        System.out.println(allPosts);

        allPosts.forEach(posts -> {
                if (!posts.isEmpty()) {
                    log.info(posts.getFirst().getSource() + " 수집된 게시글 수: " + posts.size());

                    // posts(List<Post>)를 순회하는 두 번째 루프
//                    posts.forEach(post -> {
//                        log.info(post.toString());
//                    });
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