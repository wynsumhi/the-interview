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

    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @Builder
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Getter
    public static class AiTitleRequest {

        @JsonProperty("interview_context")
        private String interviewContext;
    }

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class AiNextOpeningRequest {

        // 이전 인터뷰의 Q&A 리스트 (필수)
        @JsonProperty("interview_context")
        private String interviewContext;

        // 사용자의 닉네임 (선택)
        @JsonProperty("nickname")
        private String nickname;
    }
}
