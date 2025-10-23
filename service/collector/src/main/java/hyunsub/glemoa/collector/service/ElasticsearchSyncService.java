package hyunsub.glemoa.collector.service;

import hyunsub.glemoa.collector.document.PostDocument;
import hyunsub.glemoa.collector.repository.PostDocumentRepository;
import hyunsub.glemoa.collector.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchSyncService {

    private final PostRepository postRepository;
    private final PostDocumentRepository postDocumentRepository;

    private static final int BATCH_SIZE = 1000;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void onApplicationReady() {
        log.info("Elasticsearch 초기 데이터 동기화 작업을 시작합니다.");
        log.info("JVM Default Timezone: {}", java.util.TimeZone.getDefault().getID());

        long mysqlTotalCount = postRepository.count();
        log.info("mysqlTotalCount : {}", mysqlTotalCount);
        long elasticsearchCurrentCount = postDocumentRepository.count();
        log.info("elasticsearchCurrentCount : {}", elasticsearchCurrentCount);

        if (mysqlTotalCount == elasticsearchCurrentCount && mysqlTotalCount > 0) {
            log.info("MySQL과 Elasticsearch의 문서 수가 일치합니다 {}개. 초기 동기화를 건너킵니다.", mysqlTotalCount);
            return;
        }

        log.info("MySQL에 {}개의 게시글이 존재합니다. Elasticsearch에 {}개의 문서가 있습니다.", mysqlTotalCount, elasticsearchCurrentCount);
        log.info("Elasticsearch에 MySQL 데이터를 초기 로드합니다.");

        // 기존 Elasticsearch 데이터 삭제 (선택 사항: 중복 방지 및 깨끗한 시작을 위해)
        // postDocumentRepository.deleteAll();
        // log.info("기존 Elasticsearch 문서 {}개 삭제 완료.", elasticsearchCurrentCount);


        int pageNum = 0;
        long processedCount = 0;
        Page<hyunsub.glemoa.collector.entity.Post> postPage;

        do {
            Pageable pageable = PageRequest.of(pageNum, BATCH_SIZE);
            postPage = postRepository.findAll(pageable);

            List<PostDocument> documentsToSave = postPage.getContent().stream()
                    .map(post -> {
                        PostDocument doc = PostDocument.from(post);
//                        log.info("MySQL CreatedAt: {}, PostDocument CreatedAt: {}, System Timezone: {}",
//                                post.getCreatedAt(),
//                                doc.getCreatedAt(),
//                                ZoneId.systemDefault());
                        return doc;
                    })
                    .collect(Collectors.toList());

            if (!documentsToSave.isEmpty()) {
                postDocumentRepository.saveAll(documentsToSave);
                processedCount += documentsToSave.size();
                log.info("Elasticsearch에 {}/{}개 문서 저장 완료 ({}% 진행).",
                         processedCount, mysqlTotalCount, (processedCount * 100 / mysqlTotalCount));
            }

            pageNum++;
        } while (postPage.hasNext());

        log.info("Elasticsearch 초기 데이터 동기화 작업 완료. 총 {}개 문서 저장.", processedCount);
    }
}
