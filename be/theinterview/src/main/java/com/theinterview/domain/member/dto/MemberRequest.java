package com.theinterview.domain.member.dto;

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
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberJoinRequest {

        private String email;
        private String password;
        private String nickname;
    }
}
