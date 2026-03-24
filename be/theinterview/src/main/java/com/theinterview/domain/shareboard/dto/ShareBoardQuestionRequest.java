package com.theinterview.domain.shareboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 공유 보드 질문 관련 요청 DTO 모음
public abstract class ShareBoardQuestionRequest {

    // 질문 생성 요청 DTO
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class CreateShareBoardQuestionRequest {

        // 공백만 입력하는 경우를 막는다
        @NotBlank(message = "질문 내용은 비어 있을 수 없습니다.")
        // 질문 길이는 최대 300자로 제한한다
        @Size(max = 300, message = "질문 내용은 300자 이하여야 합니다.")
        private String content;
    }

    // 질문 수정 요청 DTO
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class UpdateShareBoardQuestionRequest {

        // 공백만 입력하는 경우를 막는다
        @NotBlank(message = "질문 내용은 비어 있을 수 없습니다.")
        // 수정 후에도 최대 길이 제한은 동일하게 유지한다
        @Size(max = 300, message = "질문 내용은 300자 이하여야 합니다.")
        private String content;
    }
}
