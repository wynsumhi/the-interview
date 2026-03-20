package com.theinterview.domain.interview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class AiRagDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Turn {

        @JsonProperty("question_id")
        private String questionId;
        @JsonProperty("answer_id")
        private String answerId;
        private String question;
        private String answer;
        @JsonProperty("turn_index")
        private int turnIndex;
        private Map<String, Object> metadata;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Interview {

        @JsonProperty("interview_id")
        private String interviewId;
        private List<Turn> turns;
        private Map<String, Object> metadata;
    }

    // --- Request DTOs ---
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReindexRequest {

        @JsonProperty("user_id")
        private String userId;
        private List<Interview> interviews;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpsertRequest {

        @JsonProperty("user_id")
        private String userId;
        @JsonProperty("interview_id")
        private String interviewId;
        private List<Turn> turns;
        private Map<String, Object> metadata;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DeleteRequest {

        @JsonProperty("user_id")
        private String userId;
        @JsonProperty("interview_id")
        private String interviewId;
    }

    // --- Response DTOs ---
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReindexResponse {

        private String status;
        private String message;
        @JsonProperty("collection_name")
        private String collectionName;
        @JsonProperty("reindexed_interview_count")
        private int reindexedInterviewCount;
        @JsonProperty("reindexed_turn_count")
        private int reindexedTurnCount;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpsertResponse {

        private String status;
        private String message;
        @JsonProperty("collection_name")
        private String collectionName;
        @JsonProperty("indexed_count")
        private int indexedCount;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeleteResponse {

        private String status;
        private String message;
        @JsonProperty("collection_name")
        private String collectionName;
        @JsonProperty("deleted_count")
        private int deletedCount;
    }
}
