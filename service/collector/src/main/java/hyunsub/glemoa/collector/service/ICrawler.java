package hyunsub.glemoa.collector.service;

import hyunsub.glemoa.collector.entity.Post;

import java.time.LocalDateTime;
import java.util.List;

public interface ICrawler {
//    List<Post> crawl();
//    List<Post> crawl(int pageCount);
    List<Post> crawl(LocalDateTime until);
}
