package com.theinterview.domain.interview.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

public abstract class FastApiResponse {

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class AiPipelineResponse {

        @JsonProperty("question_text")
        private String questionText;         // LLM이 생성한 꼬리질문

        @JsonProperty("tts_audio_base64")
        private String ttsAudioBase64;       // TTS 변환된 Base64 오디오 데이터

        @JsonProperty("tts_audio_mime_type")
        private String ttsAudioMimeType;
    }
}
