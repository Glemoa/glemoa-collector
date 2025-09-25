package hyunsub.glemoa.collector.common;

import hyunsub.glemoa.collector.config.CrawlerProperties;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicCrawlerScheduler implements InitializingBean {
    /*
        InitializingBean은 스프링 프레임워크가 제공하는 콜백 인터페이스로, Bean의 프로퍼티가 모두 설정된 후 특정 로직을 수행하도록 해준다.
     */

    private final TaskScheduler taskScheduler;
    private final CrawlerProperties crawlerProperties;
    private final PostRepository postRepository;
    // Map 타입으로 특정 인터페이스를 선언하고 주입받으면, 스프링이 해당 인터페이스를 구현하는 모든 빈(Bean)들을 자동으로 찾아와 Map에 담아줌.
    // 키(Key): 스프링 컨테이너에 등록된 빈의 이름 (일반적으로 클래스 이름의 첫 글자를 소문자로 바꾼 이름)입니다.
    // 값(Value): 해당 빈의 실제 객체 인스턴스입니다.
    private final Map<String, ICrawler> crawlers;

    @Value("${glemoa.scheduler.initial-crawl-days:1}")
    private int initialCrawlDays;

    @Value("${glemoa.batch-size:100}")
    private int batchSize;

    /*
        InitializingBean 인터페이스를 구현하면 afterPropertiesSet() 메서드를 오버라이딩하게 된다.
        스프링 컨테이너가 Bean을 생성하고 @Autowired, @Value, @RequiredArgsConstructor 등을 통해 모든 프로퍼티(변수)의 주입을 완료한 직후에 자동으로 호출된다.
     */
    @Override
    public void afterPropertiesSet() {
        log.info("동적 크롤러 스케줄러를 초기화합니다...");
        crawlerProperties.getCrawlers().forEach(config -> {
            if (config.isEnabled()) {
                ICrawler crawler = crawlers.get(config.getName());
                if (crawler != null) {
                    log.info("스케줄링 등록: {} (cron: {})", config.getName(), config.getCron());
                    /*
                        schedule(Runnable task, Trigger trigger) : Trigger라는 객체에 정의된 복잡한 규칙에 따라 작업을 실행하도록 예약합니다.
                        Runnable은 **"무엇을 해야 하는지"**를 담고 있는 코드 덩어리
                        Trigger는 cron 표현식(예: 매월 1일 0시 0분에 실행) 같은 정교한 규칙을 담을 수 있습니다.
                     */
                    taskScheduler.schedule(
                        new CrawlerJob(crawler, postRepository, initialCrawlDays, batchSize),
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
