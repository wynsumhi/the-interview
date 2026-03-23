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
        @JsonProperty("interview_context") // todo: ai 제목 생성 로직 머지 되면 변경해야함
        private String interviewContext;

        // 사용자의 닉네임 (선택)
        @JsonProperty("nickname")
        private String nickname;
    }


    // 퍼사드에서 데이터를 받아 올 목적으로 사용하는 내부 dto
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class AiPipelineReadyDto {

        private String savedFilePath; // 파일 저장 경로
        private Integer sequence; // 시퀀스
        private String nickname; // 사용자 닉네임
        private String previousQuestion; // 이전 대화 내역
        private Boolean isFirstFinished;
    }

}
