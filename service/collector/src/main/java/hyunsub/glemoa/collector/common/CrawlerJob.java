package hyunsub.glemoa.collector.common;

import hyunsub.glemoa.collector.entity.Post;
import hyunsub.glemoa.collector.repository.PostRepository;
import hyunsub.glemoa.collector.service.ICrawler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class CrawlerJob implements Runnable {

    private final ICrawler crawler;
    private final PostRepository postRepository;
    private final int initialCrawlDays;
    private final int batchSize;
    private final int lookBackMinutes;
    private final ReentrantLock crawlerLock;


    @Override
    public void run() {
        String source = crawler.getClass().getSimpleName().replace("Crawler", "").toLowerCase();

        if (crawlerLock.tryLock()) {
            log.info("[{}] 락 획득, 크롤링 작업을 시작합니다.", source);
            try {
                // 기존 로직 시작
                try {
                    LocalDateTime targetDate = null;
                    boolean isInitialCrawl = !postRepository.existsBySource(source);

                    if(isInitialCrawl) {
                        // 최초 크롤링 시
                        targetDate = LocalDateTime.now().minusDays(initialCrawlDays);
                        log.info("[{}] 최초 크롤링을 시작합니다. (지난 {}일)", source, initialCrawlDays);
                    } else {
                        // 최초 크롤링이 아닐 시 yml에 정의해둔 값에 맞춰 주기적으로 크롤링
                        targetDate = LocalDateTime.now().minusMinutes(lookBackMinutes);
                    }

                    log.info("[{}] 스케줄링된 작업 실행. (스레드: {})", source, Thread.currentThread().getName());
                    List<Post> crawledPosts = crawler.crawl(targetDate);

                    if (crawledPosts == null || crawledPosts.isEmpty()) {
                        log.info("[{}] 크롤링 완료. 수집된 게시글이 없습니다.", source);
                        return;
                    }

                    // 1. 크롤링된 데이터에서 Link만 추출
                    List<String> links = crawledPosts.stream().map(Post::getLink).collect(Collectors.toList());

                    // 2. Link를 이용해 DB에서 기존 데이터를 한 번에 조회
                    List<Post> existingPosts = postRepository.findBySourceAndLinkIn(source, links);
                    // (existingValue, newValue) -> existingValue 부분은 "만약 키가 중복되면, 기존 값을 쓰고 새 값은 버려"라는 의미의 규칙
                    Map<String, Post> existingPostsMap = existingPosts.stream().collect(Collectors.toMap(Post::getLink, post -> post, (existingValue, newValue) -> existingValue));

                    List<Post> postsToSave = new ArrayList<>();
                    int updateCount = 0;

                    // 3. 크롤링된 데이터를 순회하며 '추가'할 것과 '업데이트'할 것을 분류
                    for (Post crawledPost : crawledPosts) {
                        Post existingPost = existingPostsMap.get(crawledPost.getLink());
                        if (existingPost != null) {
                            // 3-1. 데이터가 이미 존재하면 (UPDATE)
                            boolean isUpdated = false;

                            if (!Objects.equals(existingPost.getTitle(), crawledPost.getTitle())) {
                                existingPost.setTitle(crawledPost.getTitle()); isUpdated = true;
                            }
                            if (!Objects.equals(existingPost.getCommentCount(), crawledPost.getCommentCount())) {
                                existingPost.setCommentCount(crawledPost.getCommentCount()); isUpdated = true;
                            }
                            if (!Objects.equals(existingPost.getViewCount(), crawledPost.getViewCount())) {
                                existingPost.setViewCount(crawledPost.getViewCount()); isUpdated = true;
                            }
                            if (!Objects.equals(existingPost.getRecommendationCount(), crawledPost.getRecommendationCount())) {
                                existingPost.setRecommendationCount(crawledPost.getRecommendationCount()); isUpdated = true;
                            }

                            if(isUpdated) {
                                postsToSave.add(existingPost);
                                updateCount++;
                            }
                        } else {
                            // 3-2. 데이터가 없으면 (INSERT)
                            postsToSave.add(crawledPost);
                        }
                    }

                    if (postsToSave.isEmpty()) {
                        log.info("[{}] 모든 게시글이 최신 상태입니다. 업데이트할 내용이 없습니다.", source);
                        return;
                    }

                    log.info("[{}] 크롤링 완료. 총 {}개의 게시글 중 {}개는 신규, {}개는 업데이트 대상입니다.", source, crawledPosts.size(), postsToSave.size() - updateCount, updateCount);

                    // 4. 배치 저장
                    for (int i = 0; i < postsToSave.size(); i += batchSize) {
                        List<Post> batchList = postsToSave.subList(i, Math.min(i + batchSize, postsToSave.size()));
                        postRepository.saveAll(batchList);
                        log.debug("[{}] {}/{} 게시글 저장 중...", source, Math.min(i + batchSize, postsToSave.size()), postsToSave.size());
                    }

                    log.info("[{}] {}개의 신규 게시글 저장 / {}개의 게시글 업데이트 완료.", source, postsToSave.size() - updateCount, updateCount);

                } catch (Exception e) {
                    log.error("[{}] 크롤링 작업 중 오류 발생: {}", source, e.getMessage(), e);
                }
                // 기존 로직 끝
            } finally {
                crawlerLock.unlock();
                log.info("[{}] 작업 완료, 락을 해제합니다.", source);
            }
        } else {
            log.info("[{}] 다른 크롤러가 실행 중이므로 작업을 건너뜁니다.", source);
        }
    }
}
