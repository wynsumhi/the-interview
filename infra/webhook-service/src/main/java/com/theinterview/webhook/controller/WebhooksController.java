package com.theinterview.webhook.controller;

import com.theinterview.webhook.dto.request.GitlabRequest;
import com.theinterview.webhook.dto.response.ApiResponse;
import com.theinterview.webhook.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/infra/webhook")
@RequiredArgsConstructor
public class WebhooksController {

    private final WebhookService webhookService;

    /**
     * Gitlab webhook 이벤트를 처리합니다 ( MR, Push, Merge )
     *
     * @param request gitlab에서 전송한 페이로드
     * @return Void 타입의 표준 응답을 반환합니다.
     */
    @PostMapping("/gitlab")
    public ApiResponse<Void> handleGitLab(
            @RequestBody GitlabRequest request
    ) {
        webhookService.processGitlab(request);
        return ApiResponse.OK;
    }
}
