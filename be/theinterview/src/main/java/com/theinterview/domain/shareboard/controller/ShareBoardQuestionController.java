package com.theinterview.domain.shareboard.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.theinterview.domain.shareboard.dto.ShareBoardPostitLayoutRequest.UpdateLayoutRequest;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionRequest.CreateShareBoardQuestionRequest;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionRequest.UpdateShareBoardQuestionRequest;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionResponse.BookmarkPostitSliceResponse;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionResponse.DeleteShareBoardQuestionResponse;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionResponse.ShareBoardInfoResponse;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionResponse.ShareBoardQuestionItemResponse;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionResponse.ShareBoardQuestionSliceResponse;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionResponse.StartInterviewFromQuestionResponse;
import com.theinterview.domain.shareboard.dto.ShareBoardQuestionResponse.UpdateLayoutResponse;
import com.theinterview.domain.shareboard.enums.ShareBoardPostitType;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/share-board")
public class ShareBoardQuestionController {

    // 공유 보드 관련 비즈니스 로직을 처리하는 서비스입니다.
    private final ShareBoardQuestionService shareBoardQuestionService;

    // 로그인 사용자의 본인 공유 보드 정보를 조회합니다.
    @GetMapping("/me")
    public ApiResponse<ShareBoardInfoResponse> getMyShareBoard(
            @AuthenticationPrincipal User user
    ) {
        return ApiResponse.onSuccess(shareBoardQuestionService.getMyShareBoard(user.getUsername()));
    }

    // URL의 userUuid로 특정 사용자의 공유 보드 기본 정보를 조회합니다.
    @GetMapping("/member/{userUuid}")
    public ApiResponse<ShareBoardInfoResponse> getBoardInfo(
            @PathVariable String userUuid,
            @AuthenticationPrincipal User user
    ) {
        return ApiResponse.onSuccess(shareBoardQuestionService.getBoardInfoByUserUuid(userUuid,
                user == null ? null : user.getUsername()));
    }

    // 북마크 탭에서 보여줄 BOOKMARK 포스트잇 목록을 페이지 단위로 조회합니다.
    @GetMapping("/member/{userUuid}/postits/bookmarks")
    public ApiResponse<BookmarkPostitSliceResponse> getBookmarkPostits(
            @PathVariable String userUuid,
            @RequestParam(defaultValue = "0") Integer page,
            @AuthenticationPrincipal User user
    ) {
        return ApiResponse.onSuccess(shareBoardQuestionService.getBookmarkPostits(userUuid, page,
                user == null ? null : user.getUsername()));
    }

    // 질문 탭에서 보여줄 QUESTION 포스트잇 목록을 페이지 단위로 조회합니다.
    @GetMapping("/member/{userUuid}/postits/questions")
    public ApiResponse<ShareBoardQuestionSliceResponse> getQuestionPostits(
            @PathVariable String userUuid,
            @RequestParam(defaultValue = "0") Integer page,
            @AuthenticationPrincipal User user
    ) {
        return ApiResponse.onSuccess(shareBoardQuestionService.getQuestionPostits(userUuid, page,
                user == null ? null : user.getUsername()));
    }

    // 방문자가 현재 공유 보드에 질문 포스트잇을 새로 생성합니다.
    @PostMapping("/member/{userUuid}/questions")
    public ApiResponse<ShareBoardQuestionItemResponse> createQuestion(
            @PathVariable String userUuid,
            @Valid @RequestBody CreateShareBoardQuestionRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ApiResponse.onSuccess(shareBoardQuestionService.createQuestionByUserUuid(userUuid,
                request.getContent(), user.getUsername()));
    }

    // 보드 주인 또는 질문 작성자가 포스트잇 레이아웃을 수정합니다.
    @PatchMapping("/member/{userUuid}/postits/{postitType}/{sourceId}/layout")
    public ApiResponse<UpdateLayoutResponse> updateLayout(
            @PathVariable String userUuid,
            @PathVariable ShareBoardPostitType postitType,
            @PathVariable Integer sourceId,
            @Valid @RequestBody UpdateLayoutRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ApiResponse.onSuccess(shareBoardQuestionService.updateLayout(userUuid, postitType,
                sourceId, request, user.getUsername()));
    }

    // 질문 작성자가 자신의 질문 내용을 수정합니다.
    @PatchMapping("/question/{questionId}")
    public ApiResponse<ShareBoardQuestionItemResponse> updateQuestion(
            @PathVariable Integer questionId,
            @Valid @RequestBody UpdateShareBoardQuestionRequest request,
            @AuthenticationPrincipal User user
    ) {
        return ApiResponse.onSuccess(
                shareBoardQuestionService.updateQuestion(questionId, request.getContent(),
                        user.getUsername()));
    }

    // 질문 작성자 또는 보드 주인이 질문 포스트잇을 삭제합니다.
    @DeleteMapping("/question/{questionId}")
    public ApiResponse<DeleteShareBoardQuestionResponse> deleteQuestion(
            @PathVariable Integer questionId,
            @AuthenticationPrincipal User user
    ) {
        return ApiResponse.onSuccess(
                shareBoardQuestionService.deleteQuestion(questionId, user.getUsername()));
    }

    // 보드 주인이 질문 포스트잇을 인터뷰 시작점으로 사용할 때 호출합니다.
    @PostMapping("/question/{questionId}/start-interview")
    public ApiResponse<StartInterviewFromQuestionResponse> startInterviewFromQuestion(
            @PathVariable Integer questionId,
            @AuthenticationPrincipal User user
    ) throws JsonProcessingException {
        return ApiResponse.onSuccess(
                shareBoardQuestionService.startInterviewFromQuestion(questionId,
                        user.getUsername()));
    }
}
