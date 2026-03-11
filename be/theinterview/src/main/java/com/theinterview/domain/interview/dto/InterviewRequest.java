package com.theinterview.domain.interview.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

public abstract class InterviewRequest {

    @Getter
    @NoArgsConstructor
    public static class AudioMessageRequest {

        private String roomId;

        private String audioData;

        private boolean isFinal;
    }
}
