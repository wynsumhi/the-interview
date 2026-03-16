package com.theinterview.domain.interview.controller;

import com.theinterview.domain.interview.dto.InterviewRequest.InterviewStartRequest;
import com.theinterview.domain.interview.dto.InterviewResponse.WebSocketConnectResponse;
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

    // 웹소켓 연결 및 구독할 인터뷰 방의 번호(interviewID) 및 첫 질문의 번호(questionId)를 발급
    @PostMapping
    public ApiResponse<WebSocketConnectResponse> readForConnectWebSocket(
            @RequestBody InterviewStartRequest request,
            @AuthenticationPrincipal User user
    ) {
        String email = user.getUsername();
        // 서비스 레이어 호출
        return ApiResponse.onSuccess(interviewService.issueWebSocketId(email));
    }
}
