package hyunsub.glemoa.collector.repository;

import hyunsub.glemoa.collector.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    Optional<Post> findTopBySourceOrderByCreatedAtDesc(String source);
    List<Post> findBySourceAndLinkIn(String source, List<String> links);
    boolean existsBySource(String source);
}
