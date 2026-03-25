package com.theinterview.domain.shareboard.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class ShareBoardPostitLayoutRequest {

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class UpdateLayoutRequest {

        @NotNull
        private Double rotationDeg;

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private Double positionX;

        @NotNull
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private Double positionY;

        @NotNull
        private Integer zIndex;
    }
}
