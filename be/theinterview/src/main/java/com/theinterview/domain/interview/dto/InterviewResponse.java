package com.theinterview.domain.interview.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.theinterview.domain.interview.dto.FastApiResponse.AiPipelineResponse;
import java.time.LocalDate;
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
    public static class FollowUpQuestionDTO {  // 다음 버튼을 누르면 응답으로 나갈 데이터를 담을 DTO

        private Integer interviewId; // 진행중인 인터뷰 id
        private Integer questionId; // 생성된 질문 id
        private String sttResult; // 사용자의 답변 문자열
        private String followUpQuestion; // 다음 질문 텍스트
        private String ttsAudioBase64; // 질문 tts

        public FollowUpQuestionDTO(AiPipelineResponse aiPipelineResponse, Integer interviewId,
                Integer questionId) {
            this.interviewId = interviewId;
            this.questionId = questionId;
            this.sttResult = aiPipelineResponse.getSttText();
            this.followUpQuestion = aiPipelineResponse.getQuestionText();
            this.ttsAudioBase64 = aiPipelineResponse.getTtsAudioBase64();
        }
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class WebSocketConnectResponse { // 웹소켓 연결 요청 시 응답으로 나갈 DTO

        private Integer interviewId; // 진행중인 인터뷰 id
        private Integer questionId; // 생성된 질문 id(첫 질문을 일단 생성한다고 가정함)
        private String interviewTitle; // 인터뷰 제목
        private String ttsAudioBase64; // tts 음성 파일
    }

    // 인터뷰 히스토리 전체 목록 조회
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class InterviewHistoryDTO {

        private List<InterviewHistory> interviews; // 인터뷰 정보
        private boolean hasNext; // 전체 페이지
        private int currentPage; // 현재 페이지
    }

    // 인터뷰 정보
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class InterviewHistory {

        private Integer interviewId; // 인터뷰 ID
        private String interviewTitle; // 인터뷰 제목
        private LocalDate interviewDate; // 인터뷰 생성일(년-월-일)
    }

    // 인터뷰 히스토리 상세 조회
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class InterviewHistoryDetailDTO {

        private String interviewTitle; // 인터뷰 제목
        private LocalDate interviewDate; // 인터뷰 생성일(년-월-일)
        private Boolean interviewShare; // 인터뷰 공개여부
        private List<InterviewHistoryDetail> interview; // 인터뷰 내용
    }

    // 인터뷰 내용
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class InterviewHistoryDetail {

        private Integer interviewAnswerId; // 인터뷰 질문 ID
        private String interviewQuestion; // 인터뷰 질문
        private String interviewText; // 인터뷰 답변
        private String interviewVoice; // 인터뷰 음성파일 UUID
        private Boolean interviewQAShare; // 인터뷰 질문답변 공개여부, true : 공개, 포스트잇용
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

        private String interviewTitle; // 인터뷰 제목
        private LocalDate interviewDate; // 인터뷰 생성일(년-월-일)
        private List<SharedInterviewDetail> interview; // 인터뷰 내용
    }

    // 인터뷰 내용
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class SharedInterviewDetail {

        private String interviewQuestion; // 인터뷰 질문
        private String interviewText; // 인터뷰 답변
        private String interviewVoice; // 인터뷰 음성파일 UUID
    }

    // 사용자 공유 페이지 Response( 포스트 잇 )
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class InterviewPostItBoardResponse {

        private List<InterviewPostItDetail> postIt;
        private Boolean hasNext; // 다음 페이지 유무
        private Integer currentPage; // 현재 페이지
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InterviewPostItDetail {

        private String answerKeyword;
        private String interviewQuestion;
        private String interviewAnswer;
        private String interviewUrl;
    }
}
