package hyunsub.glemoa.collector.common;

import hyunsub.glemoa.collector.document.PostDocument;
import hyunsub.glemoa.collector.entity.Post;
import hyunsub.glemoa.collector.repository.PostDocumentRepository;
import hyunsub.glemoa.collector.repository.PostRepository;
import hyunsub.glemoa.collector.service.ICrawler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

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
    private final PostDocumentRepository postDocumentRepository; // 추가: Elasticsearch Repository
    private final int initialCrawlDays;
    private final int batchSize;
    private final int lookBackMinutes;
    private final int restartCrawlMinutes;
    private final ReentrantLock crawlerLock;


    @Override
    @Transactional // 트랜잭션 관리 (MySQL 저장과 Elasticsearch 저장을 하나의 논리적 단위로 묶을 수 있음)
    public void run() {
        String source = crawler.getClass().getSimpleName().replace("Crawler", "").toLowerCase();

        if (crawlerLock.tryLock()) {
            log.info("[{}] 락 획득, 크롤링 작업을 시작합니다.", source);
            try {
                LocalDateTime targetDate;

                // 1. 최초 크롤링인지 확인
                boolean isInitialCrawl = !postRepository.existsBySource(source);

                if (isInitialCrawl) {
                    // 시나리오 1: 최초 크롤링
                    // yml의 'initial-crawl-days' 값을 사용합니다.
                    targetDate = LocalDateTime.now().minusDays(initialCrawlDays);
                    log.info("[{}] 최초 크롤링을 시작합니다. (지난 {}일)", source, initialCrawlDays);
                } else {
                    // 최초 크롤링이 아닌 경우, 마지막 게시물 시간을 확인하여 공백 발생 여부 판단
                    LocalDateTime lastPostDate = postRepository.findTopBySourceOrderByCreatedAtDesc(source)
                            .map(post -> post.getCreatedAt()) // 메서드 참조를 람다 표현식으로 변경
                            .orElse(LocalDateTime.now()); // 데이터가 없는 예외적인 경우 현재 시간으로 설정

                    // (cron 분 마다 게시글을 업데이트 및 둘러보는 시간) = lookBackMinutes 보다 마지막 게시물이 더 오래되었으면 공백 발생으로 간주
                    if (lastPostDate.isBefore(LocalDateTime.now().minusMinutes(lookBackMinutes))) {
                        // 시나리오 2: 재시작 또는 공백 발생
                        // yml의 'restart-crawl-minutes' 값을 사용합니다.
                        targetDate = LocalDateTime.now().minusMinutes(restartCrawlMinutes);
                        log.info("[{}] 크롤링 공백 감지. 재시작 크롤링을 시작합니다. (지난 {}분)", source, restartCrawlMinutes);
                    } else {
                        // 시나리오 3: 정상적인 주기적 크롤링
                        // yml의 'look-back-minutes' 값을 사용합니다.
                        targetDate = LocalDateTime.now().minusMinutes(lookBackMinutes);
                        log.info("[{}] 주기적 크롤링을 시작합니다. (지난 {}분)", source, lookBackMinutes);
                    }
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
//                List<PostDocument> postDocumentsToSave = new ArrayList<>(); // Elasticsearch에 저장할 문서 리스트
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
//                            postDocumentsToSave.add(PostDocument.from(existingPost)); // Elasticsearch 업데이트 대상
                            updateCount++;
                        }
                    } else {
                        // 3-2. 데이터가 없으면 (INSERT)
                        postsToSave.add(crawledPost);
                        // 주석 처리 해준 이유는 새로 크롤링한 데이터는 mysql에 한번 들어가지 않고 바로 엘라스틱으로 저장하는 상태라
                        // id 값이 없어서 엘라스틱 서치가 자동으로 생성해주는 id(문자열 값을 가진 이상한 id값)를 가지고 저장되기 때문이다.
//                        postDocumentsToSave.add(PostDocument.from(crawledPost)); // Elasticsearch 추가 대상
                    }
                }

                if (postsToSave.isEmpty()) {
                    log.info("[{}] 모든 게시글이 최신 상태입니다. 업데이트할 내용이 없습니다.", source);
                    return;
                }

                log.info("[{}] 크롤링 완료. 총 {}개의 게시글 중 {}개는 신규, {}개는 업데이트 대상입니다.", source, crawledPosts.size(), postsToSave.size() - updateCount, updateCount);

                // 4. MySQL 배치 저장
                for (int i = 0; i < postsToSave.size(); i += batchSize) {
                    List<Post> batchList = postsToSave.subList(i, Math.min(i + batchSize, postsToSave.size()));
                    postRepository.saveAll(batchList);
                    log.info("[{}] {}/{} 게시글 MySQL 저장 중...", source, Math.min(i + batchSize, postsToSave.size()), postsToSave.size());
                }

                // 5. Elasticsearch 배치 저장 (추가된 부분)
                // MySQL 저장이 완료된 postsToSave 리스트(ID가 채워져 있음)를 사용하여 PostDocument를 생성합니다.
                List<PostDocument> postDocumentsToSave = postsToSave.stream()
                        .map(PostDocument::from)
                        .collect(Collectors.toList());

                for (int i = 0; i < postDocumentsToSave.size(); i += batchSize) {
                    List<PostDocument> batchList = postDocumentsToSave.subList(i, Math.min(i + batchSize, postDocumentsToSave.size()));
                    postDocumentRepository.saveAll(batchList);
                    log.info("[{}] {}/{} 게시글 Elasticsearch 저장 중...", source, Math.min(i + batchSize, postDocumentsToSave.size()), postDocumentsToSave.size());
                }

                log.info("[{}] {}개의 신규 게시글 저장 / {}개의 게시글 업데이트 완료.", source, postsToSave.size() - updateCount, updateCount);

            } catch (Exception e) {
                log.error("[{}] 크롤링 작업 중 오류 발생: {}", source, e.getMessage(), e);
            } finally {
                crawlerLock.unlock();
                log.info("[{}] 작업 완료, 락을 해제합니다.", source);
            }
        } else {
            log.info("[{}] 다른 크롤러가 실행 중이므로 작업을 건너뜁니다.", source);
        }
    }
}
