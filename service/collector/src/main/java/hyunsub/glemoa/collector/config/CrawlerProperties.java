package hyunsub.glemoa.collector.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "glemoa")
public class CrawlerProperties {

    private List<CrawlerConfig> crawlers;

    @Getter
    @Setter
    public static class CrawlerConfig {
        private String name;
        private String cron;
        private int lookbackMinutes;
        private int initialCrawlDays;
        private int restartCrawlMinutes;
        private boolean enabled;
    }
}
