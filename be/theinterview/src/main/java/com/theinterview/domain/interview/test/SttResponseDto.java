package com.theinterview.domain.interview.test;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SttResponseDto {

    // 명세서에 정의된 필드명 "stt_text" 매핑
    @JsonProperty("stt_text")
    private String sttText;
}