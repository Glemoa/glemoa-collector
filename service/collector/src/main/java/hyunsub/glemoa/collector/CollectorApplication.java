package hyunsub.glemoa.collector;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@Slf4j
@EnableScheduling // 스케줄링 활성화
@SpringBootApplication
@EnableElasticsearchRepositories
public class CollectorApplication {

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        log.info("Set default timezone to Asia/Seoul");
    }

    public static void main(String[] args) {
        SpringApplication.run(CollectorApplication.class, args);
    }
}
