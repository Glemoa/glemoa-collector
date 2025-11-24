package hyunsub.glemoa.collector.service;

import hyunsub.glemoa.collector.dto.KeywordResDto;
import hyunsub.glemoa.collector.dto.NotificationReqDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "glemoa-member")
public interface MemberFeign {

    @GetMapping("/keyword/all")
    List<KeywordResDto> getAllKeyword();

    @PostMapping("/notification/create")
    void createNotifications(@RequestBody List<NotificationReqDto> dtos);
}
