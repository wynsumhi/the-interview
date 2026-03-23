package com.theinterview.domain.interview.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.theinterview.domain.interview.dto.InterviewRequest.InterviewExitRequest;
import com.theinterview.domain.interview.dto.InterviewRequest.InterviewShareRange;
import com.theinterview.domain.interview.dto.InterviewRequest.InterviewStartRequest;
import com.theinterview.domain.interview.dto.InterviewResponse.InterviewHistoryDTO;
import com.theinterview.domain.interview.dto.InterviewResponse.InterviewHistoryDetailDTO;
import com.theinterview.domain.interview.dto.InterviewResponse.WebSocketConnectResponse;
import com.theinterview.domain.interview.service.InterviewService;
import com.theinterview.domain.member.Member;
import com.theinterview.domain.member.repository.MemberRepository;
import com.theinterview.global.response.ApiResponse;
import com.theinterview.global.response.exception.GeneralException;
import com.theinterview.global.response.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/v1/interview")
public class InterviewController {

    private final InterviewService interviewService;
    private final MemberRepository memberRepository;

    // 웹소켓 연결 및 구독할 인터뷰 방의 번호(interviewID) 및 첫 질문의 번호(questionId)를 발급
    @PostMapping
    public ApiResponse<WebSocketConnectResponse> readForConnectWebSocket(
            @RequestBody InterviewStartRequest request,
            @AuthenticationPrincipal User user
    ) throws JsonProcessingException {
        String email = user.getUsername();
        // 서비스 레이어 호출
        return ApiResponse.onSuccess(interviewService.issueWebSocketId(email));
    }


    @PostMapping("/exit")
    public ApiResponse<String> exitInterview(
            @RequestBody InterviewExitRequest request,
            @AuthenticationPrincipal User user
    ) {
        String email = user.getUsername();
        // 서비스 레이어 호출
        return ApiResponse.onSuccess(interviewService.exitInterview(request, email));
    }

    // 인터뷰 목록 조회
    @GetMapping
    public ApiResponse<InterviewHistoryDTO> getInterviewsHistory(
            @RequestParam(defaultValue = "0") Integer page,
            @AuthenticationPrincipal User user
    ) {
        Member member = memberRepository.findByEmail(user.getUsername())
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));

        InterviewHistoryDTO interviewHistoryDTO = interviewService.getInterviewHistoryList(
                member.getId(), page);
        return ApiResponse.onSuccess(interviewHistoryDTO);
    }

    // 인터뷰 상세조회
    @GetMapping("/{interviewId}")
    public ApiResponse<InterviewHistoryDetailDTO> getInterviewHistoryDetail(
            @PathVariable(name = "interviewId") Integer interviewId,
            @AuthenticationPrincipal User user
    ) {
        Member member = memberRepository.findByEmail(user.getUsername())
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));

        InterviewHistoryDetailDTO interviewHistoryDetailDTO = interviewService.getInterviewHistoryDetail(
                interviewId, member.getId());
        return ApiResponse.onSuccess(interviewHistoryDetailDTO);
    }

    // 인터뷰 삭제
    @DeleteMapping("/{interviewId}")
    public ApiResponse<Void> deleteInterview(
            @PathVariable(name = "interviewId") Integer interviewId,
            @AuthenticationPrincipal User user
    ) {
        Member member = memberRepository.findByEmail(user.getUsername())
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));

        interviewService.deleteInterview(interviewId, member.getId());

        return ApiResponse.OK;
    }

    // 인터뷰 상세페이지 공개 범위 설정
    @PostMapping("/{interviewId}/share/set")
    public ApiResponse<Void> setInterviewShareRange(
            @PathVariable(name = "interviewId") Integer interviewId,
            @RequestBody InterviewShareRange request,
            @AuthenticationPrincipal User user
    ) {
        String email = user.getUsername();
        interviewService.setInterviewShareRange(email, interviewId, request);
        return ApiResponse.OK;
    }
}
