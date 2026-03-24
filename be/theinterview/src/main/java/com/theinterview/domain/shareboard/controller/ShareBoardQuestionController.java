package com.theinterview.domain.shareboard.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionRequest.CreateShareBoardQuestionRequest;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionRequest.UpdateShareBoardQuestionRequest;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionResponse.DeleteShareBoardQuestionResponse;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionResponse.ShareBoardInfoResponse;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionResponse.ShareBoardQuestionItemResponse;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionResponse.ShareBoardQuestionSliceResponse;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionResponse.StartInterviewFromQuestionResponse;
import com.theinterview.domain.shareboard.service.ShareBoardQuestionService;
import com.theinterview.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 공유 보드 질문 포스트잇 관련 REST API 컨트롤러
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/share-board")
public class ShareBoardQuestionController {

    // 실제 비즈니스 로직은 서비스에 위임한다
    private final ShareBoardQuestionService shareBoardQuestionService;

    // 로그인 사용자의 공유 보드를 조회한다
    // 아직 보드가 없으면 내부에서 자동 생성한다
    @GetMapping("/me")
    public ApiResponse<ShareBoardInfoResponse> getMyShareBoard(
            @AuthenticationPrincipal User user
    ) {
        return ApiResponse.onSuccess(
                shareBoardQuestionService.getOrCreateMyShareBoard(user.getUsername()));
    }

    // 특정 공유 보드에 질문 포스트잇을 생성한다
    @PostMapping("/{shareBoardId}/questions")
    public ApiResponse<ShareBoardQuestionItemResponse> createQuestion(
            // 질문이 달릴 대상 보드 ID
            @PathVariable Integer shareBoardId,
            // 요청 본문
            @Valid @RequestBody CreateShareBoardQuestionRequest request,
            // 로그인 사용자 정보
            @AuthenticationPrincipal User user
    ) {
        return ApiResponse.onSuccess(
                shareBoardQuestionService.createQuestion(shareBoardId, request.getContent(),
                        user.getUsername()));
    }

    // 특정 공유 보드의 질문 포스트잇 목록을 조회한다
    @GetMapping("/{shareBoardId}/questions")
    public ApiResponse<ShareBoardQuestionSliceResponse> getQuestions(
            // 조회 대상 보드 ID
            @PathVariable Integer shareBoardId,
            // 마지막 조회 기준 ID
            @RequestParam(required = false) Integer cursor,
            // 조회 개수
            @RequestParam(defaultValue = "20") Integer size,
            // 로그인 사용자 정보
            @AuthenticationPrincipal User user
    ) {
        return ApiResponse.onSuccess(
                shareBoardQuestionService.getQuestions(shareBoardId, cursor, size,
                        user.getUsername()));
    }

    // 질문 포스트잇 내용을 수정한다
    @PatchMapping("/question/{questionId}")
    public ApiResponse<ShareBoardQuestionItemResponse> updateQuestion(
            // 수정할 질문 ID
            @PathVariable Integer questionId,
            // 수정 요청 본문
            @Valid @RequestBody UpdateShareBoardQuestionRequest request,
            // 로그인 사용자 정보
            @AuthenticationPrincipal User user
    ) {
        return ApiResponse.onSuccess(
                shareBoardQuestionService.updateQuestion(questionId, request.getContent(),
                        user.getUsername()));
    }

    // 질문 포스트잇을 삭제 처리한다
    @DeleteMapping("/question/{questionId}")
    public ApiResponse<DeleteShareBoardQuestionResponse> deleteQuestion(
            // 삭제할 질문 ID
            @PathVariable Integer questionId,
            // 로그인 사용자 정보
            @AuthenticationPrincipal User user
    ) {
        return ApiResponse.onSuccess(
                shareBoardQuestionService.deleteQuestion(questionId, user.getUsername()));
    }

    // 특정 질문 포스트잇을 기반으로 인터뷰를 시작한다
    @PostMapping("/question/{questionId}/start-interview")
    public ApiResponse<StartInterviewFromQuestionResponse> startInterviewFromQuestion(
            // 인터뷰 시작에 사용할 질문 ID
            @PathVariable Integer questionId,
            // 로그인 사용자 정보
            @AuthenticationPrincipal User user
    ) throws JsonProcessingException {
        return ApiResponse.onSuccess(
                shareBoardQuestionService.startInterviewFromQuestion(questionId,
                        user.getUsername()));
    }
}
