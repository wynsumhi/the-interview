package com.theinterview.domain.shareboard.dto;

import com.theinterview.domain.interview.dto.InterviewResponse.WebSocketConnectResponse;
import com.theinterview.domain.interview.entity.InterviewAnswer;
import com.theinterview.domain.shareboard.entity.ShareBoard;
import com.theinterview.domain.shareboard.entity.ShareBoardPostitLayout;
import com.theinterview.domain.shareboard.entity.ShareBoardQuestion;
import com.theinterview.domain.shareboard.enums.ShareBoardPostitType;
import com.theinterview.domain.shareboard.enums.ShareBoardQuestionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public abstract class ShareBoardQuestionResponse {

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class ShareBoardInfoResponse {

        private Integer shareBoardId;
        private Integer ownerMemberId;
        private String ownerNickname;
        private String ownerUserUuid;
        private Boolean isOwner;

        public static ShareBoardInfoResponse from(ShareBoard shareBoard,
                Integer requesterMemberId) {
            Integer ownerId = shareBoard.getOwner().getId();
            return ShareBoardInfoResponse.builder()
                    .shareBoardId(shareBoard.getId())
                    .ownerMemberId(ownerId)
                    .ownerNickname(shareBoard.getOwner().getNickname())
                    .ownerUserUuid(shareBoard.getOwner().getUserUuid())
                    .isOwner(requesterMemberId != null && ownerId.equals(requesterMemberId))
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class ShareBoardQuestionAuthorResponse {

        private Integer memberId;
        private String nickname;
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class PostitLayoutResponse {

        private String colorCode;
        private BigDecimal rotationDeg;
        private BigDecimal positionX;
        private BigDecimal positionY;
        private Integer zIndex;

        public static PostitLayoutResponse from(ShareBoardPostitLayout layout) {
            return PostitLayoutResponse.builder()
                    .colorCode(layout.getColorCode())
                    .rotationDeg(layout.getRotationDeg())
                    .positionX(layout.getPositionX())
                    .positionY(layout.getPositionY())
                    .zIndex(layout.getZIndex())
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class PostitPermissionsResponse {

        private Boolean canEditContent;
        private Boolean canDelete;
        private Boolean canEditLayout;
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class ShareBoardQuestionItemResponse {

        private ShareBoardPostitType postitType;
        private Integer sourceId;
        private Integer questionId;
        private Integer shareBoardId;
        private ShareBoardQuestionAuthorResponse author;
        private String content;
        private ShareBoardQuestionStatus status;
        private String statusLabel;
        private Integer startedInterviewId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private PostitPermissionsResponse permissions;
        private PostitLayoutResponse layout;

        public static ShareBoardQuestionItemResponse from(ShareBoardQuestion question,
                Integer requesterMemberId, ShareBoardPostitLayout layout) {
            boolean isAuthor = requesterMemberId != null
                    && question.getAuthor().getId().equals(requesterMemberId);
            boolean isOwner = requesterMemberId != null
                    && question.getShareBoard().getOwner().getId().equals(requesterMemberId);
            boolean isUsed = question.getStatus() == ShareBoardQuestionStatus.USED;

            return ShareBoardQuestionItemResponse.builder()
                    .postitType(ShareBoardPostitType.QUESTION)
                    .sourceId(question.getId())
                    .questionId(question.getId())
                    .shareBoardId(question.getShareBoard().getId())
                    .author(ShareBoardQuestionAuthorResponse.builder()
                            .memberId(question.getAuthor().getId())
                            .nickname(question.getAuthor().getNickname())
                            .build())
                    .content(question.getContent())
                    .status(question.getStatus())
                    .statusLabel(ShareBoardQuestionResponse.getStatusLabel(question.getStatus()))
                    .startedInterviewId(question.getStartedInterview() == null ? null
                            : question.getStartedInterview().getId())
                    .createdAt(question.getCreatedAt())
                    .updatedAt(question.getUpdatedAt())
                    .permissions(PostitPermissionsResponse.builder()
                            .canEditContent(isAuthor && !isUsed)
                            .canDelete((!isUsed && (isAuthor || isOwner)) || (isUsed && isOwner))
                            .canEditLayout(isOwner || isAuthor)
                            .build())
                    .layout(layout == null ? null : PostitLayoutResponse.from(layout))
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class ShareBoardQuestionSliceResponse {

        private String ownerUserUuid;
        private List<ShareBoardQuestionItemResponse> items;
        private int currentPage;
        private boolean hasNext;
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class BookmarkPostitItemResponse {

        private ShareBoardPostitType postitType;
        private Integer sourceId;
        private String question;
        private String answer;
        private String answerKeyword;
        private Integer interviewId;
        private String interviewUrl;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private PostitPermissionsResponse permissions;
        private PostitLayoutResponse layout;

        public static BookmarkPostitItemResponse from(InterviewAnswer answer,
                ShareBoardPostitLayout layout, boolean canEditLayout, String shareboardEndpoint) {
            return BookmarkPostitItemResponse.builder()
                    .postitType(ShareBoardPostitType.BOOKMARK)
                    .sourceId(answer.getId())
                    .question(answer.getInterviewQuestion().getQuestionText())
                    .answer(answer.getAnswerText())
                    .answerKeyword(answer.getAnswerKeyword())
                    .interviewId(answer.getInterviewQuestion().getInterview().getId())
                    .interviewUrl(
                            answer.getInterviewQuestion().getInterview().getShareLink() == null
                                    ? null : shareboardEndpoint
                                    + answer.getInterviewQuestion().getInterview().getShareLink())
                    .createdAt(answer.getCreatedAt())
                    .updatedAt(layout.getUpdatedAt())
                    .permissions(PostitPermissionsResponse.builder()
                            .canEditContent(false)
                            .canDelete(false)
                            .canEditLayout(canEditLayout)
                            .build())
                    .layout(PostitLayoutResponse.from(layout))
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class BookmarkPostitSliceResponse {

        private String ownerUserUuid;
        private List<BookmarkPostitItemResponse> items;
        private int currentPage;
        private boolean hasNext;
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class DeleteShareBoardQuestionResponse {

        private Integer questionId;
        private Boolean deleted;
    }

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

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class UpdateLayoutResponse {

        private ShareBoardPostitType postitType;
        private Integer sourceId;
        private PostitLayoutResponse layout;
        private PostitPermissionsResponse permissions;

        public static UpdateLayoutResponse from(ShareBoardPostitType postitType, Integer sourceId,
                ShareBoardPostitLayout layout, boolean canEditLayout) {
            return UpdateLayoutResponse.builder()
                    .postitType(postitType)
                    .sourceId(sourceId)
                    .layout(PostitLayoutResponse.from(layout))
                    .permissions(PostitPermissionsResponse.builder()
                            .canEditContent(false)
                            .canDelete(false)
                            .canEditLayout(canEditLayout)
                            .build())
                    .build();
        }
    }

    private static String getStatusLabel(ShareBoardQuestionStatus status) {
        return switch (status) {
            case OPEN -> "WAITING";
            case USED -> "INTERVIEW_STARTED";
            case DELETED -> "DELETED";
        };
    }
}
