package hyunsub.glemoa.collector.document;

import com.fasterxml.jackson.annotation.JsonFormat;
import hyunsub.glemoa.collector.entity.Post;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Getter
@Builder
@Document(indexName = "posts") // Elasticsearch 인덱스 이름 지정
@Setting(settingPath = "elasticsearch/posts-settings.json") // 이 줄을 추가
public class PostDocument {

    @Id // Elasticsearch Document의 ID
    private Long id;

    @Field(type = FieldType.Text,
            analyzer = "korean_ngram",
            searchAnalyzer = "korean_ngram") // 한글 분석기 사용
    private String title;

    @Field(type = FieldType.Keyword) // 정확히 일치하는 검색에 사용
    private String source;

    @Field(type = FieldType.Text,
            analyzer = "korean_ngram",
            searchAnalyzer = "korean_ngram") // 한글 분석기 사용
    private String author;

    @Field(type = FieldType.Text) // 링크는 텍스트로 저장
    private String link;

    @Field(type = FieldType.Integer)
    private Integer commentCount;

    // Instant는 “UTC 절대 시각이라서 엘라스틱 서치로 저장할 때 9시간을 더해주지 않는다.
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSSSSS||uuuu-MM-dd'T'HH:mm:ss||uuuu-MM-dd'T'HH:mm")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private Instant createdAt;

    @Field(type = FieldType.Integer)
    private Integer viewCount;

    @Field(type = FieldType.Integer)
    private Integer recommendationCount;

    // Post 엔티티로부터 PostDocument를 생성하는 헬퍼 메서드
    public static PostDocument from(Post post) {
        return PostDocument.builder()
                .id(post.getId())
                .title(post.getTitle())
                .source(post.getSource())
                .author(post.getAuthor())
                .link(post.getLink())
                .commentCount(post.getCommentCount())
                .createdAt(post.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant())
                .viewCount(post.getViewCount())
                .recommendationCount(post.getRecommendationCount())
                .build();
    }
}
