package hyunsub.glemoa.collector.common;

import hyunsub.glemoa.collector.repository.PostRepository;
import hyunsub.glemoa.collector.service.ICrawler;
import hyunsub.glemoa.collector.service.impl.*;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import hyunsub.glemoa.collector.entity.Post;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebCollectorScheduler {
    private final List<ICrawler> crawlers;
    private final PostRepository postRepository;

    // 💡 새로 만든 설정 클래스를 주입받습니다.
    private final CrawlerProperties crawlerProperties;

    // 처음 실행인지 확인하는 트리거 변수
    private boolean isInitialRun = true;

    // 예시: 매 10초마다 크롤링을 실행
    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.SECONDS)
    public void runCrawlingJob() {
        // 데이터베이스가 비어있는지 확인하여 최초 실행 여부 결정
        if (isInitialRun && postRepository.count() > 0) {
            isInitialRun = false;
        }

        // 무작위 지연 시간(Random Delay) 추가하여 봇 감지 회피
        Random random = new Random();

//        int randomDelayMillis = random.nextInt(119000) + 1000; // 1초에서 120초 사이의 무작위 지연
        int randomDelayMillis = random.nextInt(1) + 1000; // 1초에서 120초 사이의 무작위 지연
        try {
            System.out.println("다음 크롤링까지 " + (double)randomDelayMillis/1000 + "초 대기합니다.");
            Thread.sleep(randomDelayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("스케줄링된 크롤링 작업을 시작합니다.");

        // 여러 스레드를 사용하여 병렬로 크롤링을 실행할 수 있습니다.
        List<List<Post>> allPosts = crawlers.parallelStream()
                .map(crawler -> {
                    try {
                        // 💡 크롤러의 클래스 이름으로 페이지 수를 가져옵니다.
                        String crawlerName = crawler.getClass().getSimpleName().replace("Crawler", "").toLowerCase();
                        int pageCount = isInitialRun
                                ? crawlerProperties.getInitialPages().getOrDefault(crawlerName, 1)
                                : crawlerProperties.getScheduledPages().getOrDefault(crawlerName, 1);

                        System.out.println(crawler.getClass().getSimpleName() + " 크롤러를 " + pageCount + "페이지까지 실행합니다.");
                        Thread.sleep((int)(Math.random() * 4000) + 1000);

                        // ... (기존 크롤링 로직) ...
                        if (crawler instanceof PpomppuCrawler) {
                            return ((PpomppuCrawler) crawler).crawl(pageCount);
                        } else if (crawler instanceof FmkoreaCrawler) {
                            return ((FmkoreaCrawler) crawler).crawl(pageCount);
                        } else if (crawler instanceof MlbparkCrawler) {
                            return ((MlbparkCrawler) crawler).crawl(pageCount);
                        } else if (crawler instanceof ArcaLiveCrawler) {
                            return ((ArcaLiveCrawler) crawler).crawl(pageCount);
                        } else if (crawler instanceof TheqooCrawler) {
                            return ((TheqooCrawler) crawler).crawl(pageCount);
                        } else if (crawler instanceof BobaedreamCrawler) {
                            return ((BobaedreamCrawler) crawler).crawl(pageCount);
                        } else if (crawler instanceof ClienCrawler) {
                            return ((ClienCrawler) crawler).crawl(pageCount);
                        } else if (crawler instanceof EtolandCrawler) {
                            return ((EtolandCrawler) crawler).crawl(pageCount);
                        } else if (crawler instanceof HumorunivCrawler) {
                            return ((HumorunivCrawler) crawler).crawl(pageCount);
                        } else if (crawler instanceof DcInsideCrawler) {
                            return ((DcInsideCrawler) crawler).crawl(pageCount);
                        }

                        return crawler.crawl();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("크롤링 중 스레드 중단", e);
                    }
                })
                .collect(Collectors.toList());

        // 크롤링된 모든 데이터를 통합하여 처리합니다.
        System.out.println("모든 크롤링 작업이 완료되었습니다.");
        allPosts.forEach(posts -> {
                if (!posts.isEmpty()) {
                    System.out.println(posts.getFirst().getSource() + " 수집된 게시글 수: " + posts.size());

                    // posts(List<Post>)를 순회하는 두 번째 루프
                    posts.forEach(post -> {
                        System.out.println(post.toString());
                    });
                }
            });
        // TODO: 수집된 데이터를 데이터베이스에 저장하거나 웹 사이트에 노출하는 로직을 추가합니다.
        // 모든 List<Post>를 하나의 List<Post>로 통합
        List<Post> allCollectedPosts = allPosts.stream()
                .flatMap(List::stream) // List<List<Post>>를 List<Post>로 변환
                .collect(Collectors.toList());

        // 통합된 리스트가 비어있지 않은 경우에만 저장
        if (!allCollectedPosts.isEmpty()) {
            System.out.println("총 " + allCollectedPosts.size() + "개의 게시글을 데이터베이스에 저장합니다.");
            postRepository.saveAll(allCollectedPosts);
            System.out.println("총 " + allCollectedPosts.size() + "개의 게시글을 데이터베이스에 저장했습니다.");
            System.out.println("저장 완료!");
        }
    }
}