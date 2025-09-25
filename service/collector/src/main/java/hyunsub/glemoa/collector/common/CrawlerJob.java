package hyunsub.glemoa.collector.common;

import hyunsub.glemoa.collector.entity.Post;
import hyunsub.glemoa.collector.repository.PostRepository;
import hyunsub.glemoa.collector.service.ICrawler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class CrawlerJob implements Runnable {

    private final ICrawler crawler;
    private final PostRepository postRepository;
    private final int initialCrawlDays;
    private final int batchSize;

//@Value("${glemoa.batch-size:100}")
//private final int BATCH_SIZE;

    @Override
    public void run() {
        String source = crawler.getClass().getSimpleName().replace("Crawler", "").toLowerCase();
        try {
            LocalDateTime targetDate = postRepository.findTopBySourceOrderByCreatedAtDesc(source)
                    .map(Post::getCreatedAt)
                    .orElse(LocalDateTime.now().minusDays(initialCrawlDays));

            log.info("[{}] 스케줄링된 작업 실행. (스레드: {})", source, Thread.currentThread().getName());
            List<Post> posts = crawler.crawl(targetDate);

            if (posts != null && !posts.isEmpty()) {
                log.info("[{}] 크롤링 완료. {}개의 게시글을 배치 저장합니다.", source, posts.size());

//                final int BATCH_SIZE = 100; // 한 번에 저장할 배치 크기
                for (int i = 0; i < posts.size(); i += batchSize) {
                    log.info("테스트");
                    List<Post> batchList = posts.subList(i, Math.min(i + batchSize, posts.size()));
                    postRepository.saveAll(batchList);
                    log.debug("[{}] {}/{} 게시글 저장 중...", source, Math.min(i + batchSize, posts.size()), posts.size());
                }

                log.info("[{}] 저장 완료.", source);
            } else {
                log.info("[{}] 크롤링 완료. 수집된 게시글이 없습니다.", source);
            }
        } catch (Exception e) {
            log.error("[{}] 크롤링 작업 중 오류 발생: {}", source, e.getMessage(), e);
        }
    }
}
