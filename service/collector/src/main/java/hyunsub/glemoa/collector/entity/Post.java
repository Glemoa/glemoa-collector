package hyunsub.glemoa.collector.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "post")
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 각 커뮤니티의 고유 게시글 번호입니다.
    private Long sourceId;

    private String title;
    private String link;
    private String author;
    private Integer commentCount;
    private Integer viewCount;
    private Integer recommendationCount;
    private LocalDateTime createdAt;
    private String source; // 어느 커뮤니티에서 왔는지 식별
}



