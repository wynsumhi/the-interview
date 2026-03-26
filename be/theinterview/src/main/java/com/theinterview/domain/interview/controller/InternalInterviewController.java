package com.theinterview.domain.interview.controller;

import com.theinterview.domain.interview.dto.InterviewRequest.SaveRefinedAnswerRequest;
import com.theinterview.domain.interview.service.InterviewService;
import com.theinterview.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/interview/internal")
public class InternalInterviewController {

    private final InterviewService interviewService;

    @PostMapping("/answers/refined")
    public ApiResponse<Void> saveRefinedAnswer(
            @RequestBody SaveRefinedAnswerRequest request
    ) {
        /*
         * 이 엔드포인트는 AI 서버가 정제 텍스트를 별도 콜백으로 보낼 때만 사용한다.
         * 사용자가 직접 호출하는 공개 API가 아니므로,
         * 실제 인증은 SecurityConfig에 추가한 InternalApiTokenFilter에서 선행 차단된다.
         */
        log.info("AI 저장요청---------------------------------------");
        interviewService.saveRefinedAnswer(
                request.getInterviewId(),
                request.getQuestionId(),
                request.getRefinedAnswer()
        );
        return ApiResponse.OK;
    }
}
