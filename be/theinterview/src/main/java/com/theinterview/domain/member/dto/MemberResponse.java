package com.theinterview.domain.member.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.theinterview.domain.member.Member;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public abstract class MemberResponse {

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @Builder
    public static class MemberInfoDTO {

        private Integer memberId;
        private String email;
        private String nickname;

        public MemberInfoDTO(Member member) {
            this.memberId = member.getId();
            this.email = member.getEmail();
            this.nickname = member.getNickname();
        }
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    public static class TokenResponseDTO {

        @JsonIgnore
        private String refreshToken;

        private String accessToken;

        @JsonIgnore
        private long refreshTokenValidityTime;
        @JsonIgnore
        private long accessTokenValidityTime;
    }

    /**
     * 로그인 응답 테스트 DTO
     */
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    public static class LoginTestDTO {

        private Integer memberId;
        private String email;
        private String nickname;

        public LoginTestDTO(Member member) {
            this.memberId = member.getId();
            this.email = member.getEmail();
            this.nickname = member.getNickname();
        }
    }
}
