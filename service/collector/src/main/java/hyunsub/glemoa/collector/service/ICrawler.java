package hyunsub.glemoa.collector.service;

import hyunsub.glemoa.collector.entity.Post;

import java.util.List;

public interface ICrawler {
    List<Post> crawl();
    List<Post> crawl(int pageCount);
}
