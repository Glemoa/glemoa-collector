package hyunsub.glemoa.collector.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KeywordResDto {
    private Long keywordId;
    private Long memberId;
    private String keywordName;
}
