package com.theinterview.domain.interview.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public abstract class FastApiRequest {

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @JsonInclude(Include.NON_NULL)
    @Builder
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class AiStartTtsRequest {

        @JsonProperty("nickname")
        private String nickname;

        @JsonProperty("is_first_finished")
        private Boolean isFirstFinished;

        @JsonProperty("db_question")
        private String dbQuestion;
    }
}
