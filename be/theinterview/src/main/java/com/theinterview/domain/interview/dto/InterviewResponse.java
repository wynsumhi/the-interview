package com.theinterview.domain.interview.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.theinterview.domain.interview.dto.FastApiResponse.AiPipelineResponse;
import com.theinterview.domain.interview.entity.InterviewAnswer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public abstract class InterviewResponse {

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FollowUpQuestionDTO {

        // 진행중인 인터뷰 id
        private Integer interviewId;
        // 생성된 질문 id
        private Integer questionId;
        // 사용자의 답변 문자열
        private String sttResult;
        // 다음 질문 텍스트
        private String followUpQuestion;
        // 질문 tts
        private String ttsAudioBase64;
        // 종료 신호 필드
        private Boolean isExit;

        public FollowUpQuestionDTO(AiPipelineResponse aiPipelineResponse, Integer interviewId,
                Integer questionId) {
            this.interviewId = interviewId;
            this.questionId = questionId;
            this.sttResult = aiPipelineResponse.getRefinedAnswer();
            this.followUpQuestion = aiPipelineResponse.getQuestionText();
            this.ttsAudioBase64 = aiPipelineResponse.getTtsAudioBase64();
        }
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class WebSocketConnectResponse {

        // 진행중인 인터뷰 id
        private Integer interviewId;
        // 생성된 질문 id(첫 질문을 일단 생성한다고 가정함)
        private Integer questionId;
        // 인터뷰 제목
        private String interviewTitle;
        // tts 음성 파일
        private String ttsAudioBase64;
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class InterviewHistoryDTO {

        // 인터뷰 정보
        private List<InterviewHistory> interviews;
        // 전체 페이지
        private boolean hasNext;
        // 현재 페이지
        private int currentPage;
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class InterviewHistory {

        // 인터뷰 ID
        private Integer interviewId;
        // 인터뷰 제목
        private String interviewTitle;
        // 인터뷰 생성일(년-월-일)
        private LocalDate interviewDate;
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class InterviewHistoryDetailDTO {

        // 인터뷰 제목
        private String interviewTitle;
        // 인터뷰 생성일(년-월-일)
        private LocalDate interviewDate;
        // 인터뷰 공개여부
        private Boolean interviewShare;
        // 인터뷰 내용
        private List<InterviewHistoryDetail> interview;
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class InterviewHistoryDetail {

        // 인터뷰 질문 ID
        private Integer interviewAnswerId;
        // 인터뷰 질문
        private String interviewQuestion;
        // 인터뷰 답변
        private String interviewText;
        // 인터뷰 음성파일 UUID
        private String interviewVoice;
        // 인터뷰 질문답변 공개여부, true : 공개, 포스트잇용
        private Boolean interviewQAShare;
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class SttChunkResponse {

        @JsonProperty("stt_text")
        private String chunkText;
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class InterviewShareLink {

        private String shareLink;
    }

    // 공유된 인터뷰 조회
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class SharedInterviewResponseDTO {

        // 인터뷰 제목
        private String interviewTitle;
        // 인터뷰 생성일(년-월-일)
        private LocalDate interviewDate;
        // 인터뷰 내용
        private List<SharedInterviewDetail> interview;
    }

    // 인터뷰 내용
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class SharedInterviewDetail {

        // 인터뷰 질문
        private String interviewQuestion;
        // 인터뷰 답변
        private String interviewText;
        // 인터뷰 음성파일 UUID
        private String interviewVoice;
    }

    // 사용자 공유 페이지 Response( 포스트 잇 )
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class InterviewPostItBoardResponse {

        // 보드 주인의 user uuid
        private String ownerUserUuid;
        // 보드 주인의 회원 id
        private Integer ownerMemberId;
        // 보드 주인의 닉네임
        private String ownerNickname;
        // 포스트잇 목록
        private List<InterviewPostItDetail> postIts;
        // 다음 페이지 유무
        private Boolean hasNext;
        // 현재 페이지
        private Integer currentPage;
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class InterviewPostItDetail {

        // 답변 id
        private Integer interviewAnswerId;
        // 인터뷰 id
        private Integer interviewId;
        // 답변 키워드
        private String answerKeyword;
        // 인터뷰 질문
        private String interviewQuestion;
        // 인터뷰 답변
        private String interviewAnswer;
        // 포스트잇 생성 시각
        private LocalDateTime createdAt;
        // 공유 인터뷰 링크
        private String interviewUrl;

        // 엔티티를 포스트잇 응답 DTO로 변환한다
        public static InterviewPostItDetail from(InterviewAnswer answer) {
            return InterviewPostItDetail.builder()
                    // 답변 id
                    .interviewAnswerId(answer.getId())
                    // 소속 인터뷰 id
                    .interviewId(answer.getInterviewQuestion().getInterview().getId())
                    // 답변 키워드
                    .answerKeyword(answer.getAnswerKeyword())
                    // 질문 본문
                    .interviewQuestion(answer.getInterviewQuestion().getQuestionText())
                    // 답변 본문
                    .interviewAnswer(answer.getAnswerText())
                    // 생성 시각
                    .createdAt(answer.getCreatedAt())
                    // 공유 링크가 존재할 때만 full url 조립
                    .interviewUrl(
                            answer.getInterviewQuestion().getInterview().getShareLink() == null
                                    ? null
                                    : "https://j14a307.p.ssafy.io/interview/share/"
                                            + answer.getInterviewQuestion().getInterview()
                                            .getShareLink())
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InterviewExploreResponse {

        private List<InterviewExploreDetail> interviews;
        // 다음 페이지 유무
        private Boolean hasNext;
        // 현재 페이지
        private Integer currentPage;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InterviewExploreDetail {

        private Integer interviewAnswerCount;
        private Integer interviewId;
        private LocalDate interviewDate;
        private String interviewTitle;
        private String answerText;
        private String fusedScores;
        private String shareUuid;
    }
}
