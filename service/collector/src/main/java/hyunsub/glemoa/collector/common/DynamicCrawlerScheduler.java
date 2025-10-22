package hyunsub.glemoa.collector.common;

import hyunsub.glemoa.collector.config.CrawlerProperties;
import hyunsub.glemoa.collector.repository.PostDocumentRepository;
import hyunsub.glemoa.collector.repository.PostRepository;
import hyunsub.glemoa.collector.service.ICrawler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicCrawlerScheduler implements InitializingBean {

    private final TaskScheduler taskScheduler;
    private final CrawlerProperties crawlerProperties;
    private final PostRepository postRepository;
    private final PostDocumentRepository postDocumentRepository; // 추가: Elasticsearch Repository
    private final Map<String, ICrawler> crawlers;

    // [수정] 단일 공용 락 -> 크롤러별 개별 락을 보관하는 Map으로 변경
    private final Map<String, ReentrantLock> crawlerLocks = new ConcurrentHashMap<>();

    @Value("${glemoa.batch-size:100}")
    private int batchSize;

    @Override
    public void afterPropertiesSet() {
        log.info("동적 크롤러 스케줄러를 초기화합니다...");
        crawlerProperties.getCrawlers().forEach(config -> {
            if (config.isEnabled()) {
                ICrawler crawler = crawlers.get(config.getName());
                if (crawler != null) {
                    log.info("스케줄링 등록: {} (cron: {})", config.getName(), config.getCron());

                    // [수정] 크롤러 이름에 맞는 개별 락을 찾거나 새로 생성
                    ReentrantLock individualLock = crawlerLocks.computeIfAbsent(config.getName(), k -> new ReentrantLock());

                    taskScheduler.schedule(
                        // [수정] 개별 락(individualLock)을 CrawlerJob에 전달
                        new CrawlerJob(crawler, postRepository, postDocumentRepository, config.getInitialCrawlDays(), batchSize,
                                config.getLookbackMinutes(), config.getRestartCrawlMinutes(), individualLock),
                        new CronTrigger(config.getCron())
                    );
                } else {
                    log.warn("설정된 크롤러 Bean을 찾을 수 없습니다: {}", config.getName());
                }
            } else {
                log.info("비활성화된 크롤러: {}", config.getName());
            }
        });
    }
}