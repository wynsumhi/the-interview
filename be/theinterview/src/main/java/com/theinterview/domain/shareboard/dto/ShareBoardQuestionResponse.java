package com.theinterview.domain.shareboard.dto;

import com.theinterview.domain.interview.dto.InterviewResponse.WebSocketConnectResponse;
import com.theinterview.domain.shareboard.entity.ShareBoard;
import com.theinterview.domain.shareboard.entity.ShareBoardQuestion;
import com.theinterview.domain.shareboard.enums.ShareBoardQuestionStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 공유 보드 질문 관련 응답 DTO 모음
public abstract class ShareBoardQuestionResponse {

    // "내 공유 보드" 조회 응답 DTO
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class ShareBoardInfoResponse {
        private Integer shareBoardId;
        private Integer ownerMemberId;
        private String ownerNickname;
        private Boolean shareRange;

        // 엔티티를 응답 DTO 로 변환한다
        public static ShareBoardInfoResponse from(ShareBoard shareBoard) {
            return ShareBoardInfoResponse.builder()
                    // 보드 ID
                    .shareBoardId(shareBoard.getId())
                    // 소유자 회원 ID
                    .ownerMemberId(shareBoard.getOwner().getId())
                    // 소유자 닉네임
                    .ownerNickname(shareBoard.getOwner().getNickname())
                    // 공개 여부
                    .shareRange(shareBoard.getShareRange())
                    .build();
        }
    }

    // 질문 작성자 정보 응답 DTO
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class ShareBoardQuestionAuthorResponse {
        private Integer memberId;
        private String nickname;
    }

    // 질문 포스트잇 단건 응답 DTO
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class ShareBoardQuestionItemResponse {
        private Integer questionId;
        private Integer shareBoardId;
        private ShareBoardQuestionAuthorResponse author;
        private String content;
        private ShareBoardQuestionStatus status;
        private String statusLabel;
        private Integer startedInterviewId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private Boolean isMine;
        private Boolean canEdit;
        private Boolean canDelete;

        public static ShareBoardQuestionItemResponse from(ShareBoardQuestion question,
                Integer requesterMemberId) {
            // 현재 로그인 사용자가 작성자인지 계산
            boolean isMine = question.getAuthor().getId().equals(requesterMemberId);
            // 현재 로그인 사용자가 보드 주인인지 계산
            boolean isOwner = question.getShareBoard().getOwner().getId().equals(requesterMemberId);
            // 이미 인터뷰 시작에 사용된 질문인지 계산
            boolean isUsed = question.getStatus() == ShareBoardQuestionStatus.USED;

            return ShareBoardQuestionItemResponse.builder()
                    // 질문 ID
                    .questionId(question.getId())
                    // 소속 보드 ID
                    .shareBoardId(question.getShareBoard().getId())
                    // 작성자 정보 구성
                    .author(ShareBoardQuestionAuthorResponse.builder()
                            .memberId(question.getAuthor().getId())
                            .nickname(question.getAuthor().getNickname())
                            .build())
                    // 질문 본문
                    .content(question.getContent())
                    // 질문 상태
                    .status(question.getStatus())
                    // 프론트에서 바로 보여줄 상태 라벨
                    .statusLabel(ShareBoardQuestionResponse.getStatusLabel(question.getStatus()))
                    // 시작된 인터뷰가 있으면 인터뷰 ID 노출
                    .startedInterviewId(
                            question.getStartedInterview() == null ? null
                                    : question.getStartedInterview().getId())
                    // 생성 시각
                    .createdAt(question.getCreatedAt())
                    // 수정 시각
                    .updatedAt(question.getUpdatedAt())
                    // 본인 작성 질문 여부
                    .isMine(isMine)
                    // 본인이 작성했고 아직 사용되지 않은 질문만 수정 가능
                    .canEdit(isMine && !isUsed)
                    // 삭제 권한 계산
                    // - 미사용 질문은 작성자 또는 보드 주인 가능
                    // - 사용된 질문은 보드 주인만 가능
                    .canDelete((!isUsed && (isMine || isOwner)) || (isUsed && isOwner))
                    .build();
        }
    }

    // 질문 보드 목록 응답 DTO
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class ShareBoardQuestionSliceResponse {
        private List<ShareBoardQuestionItemResponse> questions;
        private Integer nextCursor;
        private boolean hasNext;
    }

    // 질문 삭제 응답 DTO
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class DeleteShareBoardQuestionResponse {
        private Integer questionId;
        private Boolean deleted;
    }

    // 질문 기반 인터뷰 시작 응답 DTO
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class StartInterviewFromQuestionResponse {
        private Integer sourceQuestionId;
        private ShareBoardQuestionStatus questionStatus;
        private String questionStatusLabel;
        private WebSocketConnectResponse interview;
    }

    // enum 상태를 프론트 표시용 문자열로 바꾼다
    // 현재는 인코딩 이슈를 피하기 위해 ASCII 라벨을 사용한다
    private static String getStatusLabel(ShareBoardQuestionStatus status) {
        return switch (status) {
            case OPEN -> "WAITING";
            case USED -> "INTERVIEW_STARTED";
            case DELETED -> "DELETED";
        };
    }
}
