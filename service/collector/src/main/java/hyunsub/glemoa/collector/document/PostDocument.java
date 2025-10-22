package hyunsub.glemoa.collector.document;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.LocalDateTime;

@Getter
@Builder
@Document(indexName = "posts") // Elasticsearch 인덱스 이름 지정
@Setting(settingPath = "/elasticsearch/posts-settings.json") // 이 줄을 추가
public class PostDocument {

    @Id // Elasticsearch Document의 ID
    private Long id;

    @Field(type = FieldType.Text, analyzer = "korean", searchAnalyzer = "korean") // 한글 분석기 사용
    private String title;

    @Field(type = FieldType.Keyword) // 정확히 일치하는 검색에 사용
    private String source;

    @Field(type = FieldType.Text, analyzer = "korean", searchAnalyzer = "korean") // 한글 분석기 사용
    private String author;

    @Field(type = FieldType.Text) // 링크는 텍스트로 저장
    private String link;

    @Field(type = FieldType.Integer)
    private Integer commentCount;

    @Field(type = FieldType.Date)
    private LocalDateTime createdAt;

    @Field(type = FieldType.Integer)
    private Integer viewCount;

    @Field(type = FieldType.Integer)
    private Integer recommendationCount;

    // Post 엔티티로부터 PostDocument를 생성하는 헬퍼 메서드
    public static PostDocument from(hyunsub.glemoa.collector.entity.Post post) {
        return PostDocument.builder()
                .id(post.getId())
                .title(post.getTitle())
                .source(post.getSource())
                .author(post.getAuthor())
                .link(post.getLink())
                .commentCount(post.getCommentCount())
                .createdAt(post.getCreatedAt())
                .viewCount(post.getViewCount())
                .recommendationCount(post.getRecommendationCount())
                .build();
    }
}
