package com.theinterview.domain.interview.test;

import lombok.Getter;
import lombok.NoArgsConstructor;

// 청크 수신용 DTO
@Getter
@NoArgsConstructor
public class TestAudioChunkDto {

    private int index; // 프론트엔드가 부여한 순번 (0, 1, 2...)
    private String audioData; // Base64 인코딩된 음성 데이터
}