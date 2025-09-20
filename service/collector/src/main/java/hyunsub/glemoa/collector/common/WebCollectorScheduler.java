package hyunsub.glemoa.collector.common;

import hyunsub.glemoa.collector.service.ICrawler;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import hyunsub.glemoa.collector.entity.Post;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebCollectorScheduler {
    private final List<ICrawler> crawlers;

    // 예시: 매 10초마다 크롤링을 실행
    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.SECONDS)
    public void runCrawlingJob() {
        System.out.println("스케줄링된 크롤링 작업을 시작합니다.");

        // 여러 스레드를 사용하여 병렬로 크롤링을 실행할 수 있습니다.
        List<List<Post>> allPosts = crawlers.parallelStream()
                .map(crawler -> {
                    System.out.println(crawler.getClass().getSimpleName() + " 크롤러를 실행합니다.");
                    return crawler.crawl();
                })
                .collect(Collectors.toList());

        // 크롤링된 모든 데이터를 통합하여 처리합니다.
        System.out.println("모든 크롤링 작업이 완료되었습니다.");
        allPosts.forEach(posts -> System.out.println(posts.getFirst().getSource() + " 수집된 게시글 수: " + posts.size()));
        // TODO: 수집된 데이터를 데이터베이스에 저장하거나 웹 사이트에 노출하는 로직을 추가합니다.
    }
}