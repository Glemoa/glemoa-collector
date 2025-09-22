package hyunsub.glemoa.collector.common;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "crawler")
public class CrawlerProperties {
    private Map<String, Integer> pages;
}
