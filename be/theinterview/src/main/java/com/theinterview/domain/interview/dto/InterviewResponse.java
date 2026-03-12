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
    public static class FollowUpQuestionDTO {

        private String roomId;
        private String sttResult;
        private String followUpQuestion;
        private String ttsAudioBase64;
    }
}
