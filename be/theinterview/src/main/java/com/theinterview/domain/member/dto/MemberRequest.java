package com.theinterview.domain.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public abstract class MemberRequest {


    /**
     * 회원가입 시 입력으로 사용하는 DTO
     */
    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class MemberJoinRequest {

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        private String email;

        @NotBlank(message = "닉네임은 필수입니다.")
        private String nickname;

        @NotBlank(message = "비밀번호는 필수입니다.")
        // 정규표현식 활용, 비밀번호 값 검증
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%])[A-Za-z\\d!@#$%]{8,12}$",
                message = "비밀번호는 영어, 숫자, 특수기호(!,@,#,$,%)를 포함하여 8~12글자여야 합니다.")
        private String password;

    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class LoginRequestDTO {

        private String email;
        private String password;
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class MemberInfoUpdateRequest {

        @NotBlank(message = "닉네임은 비어있을 수 없습니다.")
        @Pattern(regexp = "^[a-zA-Z가-힣]{3,8}$",
                message = "닉네임은 3~8자의 영문 혹은 한글만 가능합니다.")
        private String nickname;
    }

}
