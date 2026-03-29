package com.theinterview.domain.interview.test;

import lombok.Getter;
import lombok.NoArgsConstructor;

// 종료 신호용 DTO
@Getter
@NoArgsConstructor
public class TestAudioEndDto {

    private int totalCount; // 프론트엔드가 보낸 총 청크 개수
}
