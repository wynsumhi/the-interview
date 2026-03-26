package com.theinterview.domain.shareboard.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
        @Min(-19)
        @Max(19)
        private Integer positionX;

        @NotNull
        @Min(-49)
        @Max(49)
        private Integer positionY;

        @NotNull
        private Integer zIndex;
    }
}
