package com.theinterview.global.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.OptionalDouble;
import org.springframework.stereotype.Component;

@Component
public class AvgDecimal {

    // 감정 수치 형식 통일을 위한 헬퍼 메서드
    public BigDecimal toAvgDecimal(OptionalDouble avg) {
        return BigDecimal.valueOf(avg.orElse(0.0))
                .setScale(3, RoundingMode.HALF_UP);
    }

}
