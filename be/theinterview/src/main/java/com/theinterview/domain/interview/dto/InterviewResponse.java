package com.theinterview.domain.interview.dto;


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
}
