package com.theinterview.domain.interview.controller;

import com.theinterview.domain.interview.dto.InterviewRequest.InterviewStartRequest;
import com.theinterview.domain.interview.service.InterviewService;
import com.theinterview.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/v1/interview")
public class InterviewController {

    private final InterviewService interviewService;

    // 방 생성을 위한 API
    @PostMapping
    public ApiResponse<Integer> readForConnectWebSocket(
            @RequestBody InterviewStartRequest request,
            @AuthenticationPrincipal User user
    ) {
        String email = user.getUsername();
        // 서비스 레이어 호출
        return ApiResponse.onSuccess(interviewService.issueWebSocketId(email));
    }
}
