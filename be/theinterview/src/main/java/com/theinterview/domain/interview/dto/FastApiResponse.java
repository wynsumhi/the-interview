package com.theinterview.domain.interview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public abstract class FastApiResponse {

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class AiPipelineResponse {

        @JsonProperty("question_text")
        private String questionText;

        @JsonProperty("tts_audio_base64")
        private String ttsAudioBase64;

        @JsonProperty("tts_audio_mime_type")
        private String ttsAudioMimeType;

        @JsonProperty("refined_answer")
        private String refinedAnswer;

        @JsonProperty("interview_state_db")
        private InterviewStateDb interviewStateDb;

        @Getter
        @NoArgsConstructor(access = AccessLevel.PROTECTED)
        public static class InterviewStateDb {

            @JsonProperty("fused_scores")
            private FusedScores fusedScores;

            @Getter
            @NoArgsConstructor(access = AccessLevel.PROTECTED)
            public static class FusedScores {

                @JsonProperty("calm")
                private BigDecimal calm;

                @JsonProperty("tense")
                private BigDecimal tense;

                @JsonProperty("anxious")
                private BigDecimal anxious;

                @JsonProperty("low")
                private BigDecimal low;

                @JsonProperty("engaged")
                private BigDecimal engaged;
            }
        }
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class AiStartTtsResponse {

        @JsonProperty("question_text")
        private String questionText;

        @JsonProperty("tts_audio_base64")
        private String ttsAudioBase64;

        @JsonProperty("tts_audio_mime_type")
        private String ttsAudioMimeType;
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class AiTitleResponse {

        private String title;
    }

    @Getter
    @NoArgsConstructor
    public static class AiNextOpeningResponse {

        // DB에 저장될 시작 질문 텍스트
        @JsonProperty("db_question")
        private String dbQuestion;
    }

    // 키워드 응답으로 사용하는 DTO
    @Getter
    @NoArgsConstructor
    public static class KeywordResponse {

        private String keyword;
    }

    @Getter
    @NoArgsConstructor
    public static class LastAnswerTextResponse {

        @JsonProperty("refined_answer")
        private String refinedAnswer;
    }

    @Getter
    @NoArgsConstructor
    public static class LastAnswerEmotionResponse {

        @JsonProperty("interview_state_db")
        private InterviewStateDb interviewStateDb;

        @Getter
        @NoArgsConstructor
        public static class InterviewStateDb {

            @JsonProperty("top_state")
            private String topState;

            @JsonProperty("fused_scores")
            private Map<String, Double> fusedScores;

            public FusedScores toFusedScores() {
                return FusedScores.builder()
                        .calm(fusedScores.getOrDefault("calm", 0.0))
                        .tense(fusedScores.getOrDefault("tense", 0.0))
                        .engaged(fusedScores.getOrDefault("engaged", 0.0))
                        .low(fusedScores.getOrDefault("low", 0.0))
                        .anxious(fusedScores.getOrDefault("anxious", 0.0))
                        .build();
            }
        }

        @Getter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class FusedScores {

            private Double calm;
            private Double tense;
            private Double engaged;
            private Double low;
            private Double anxious;
        }
    }
}
