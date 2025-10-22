package hyunsub.glemoa.collector.repository;

import hyunsub.glemoa.collector.document.PostDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface PostDocumentRepository extends ElasticsearchRepository<PostDocument, Long> {
    // glemoa-collector에서는 검색 기능이 필요 없으므로 추가적인 쿼리 메서드는 필요 없습니다.
    // 단순히 저장(save) 기능만 사용합니다.
}
