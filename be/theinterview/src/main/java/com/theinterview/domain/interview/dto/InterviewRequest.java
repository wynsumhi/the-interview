package com.theinterview.domain.interview.dto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

public abstract class InterviewRequest {

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class AudioMessageRequest {

        private Integer interviewId;  // 웹소켓 방 번호 == interviewId
        private Integer questionId; // 답변한 질문 id
        private String audioData; // 음성 data(base64)
        private boolean isFinal;  // 마지막 질문 여부
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class InterviewStartRequest {

        private boolean isPrevAffected;
    }
}
