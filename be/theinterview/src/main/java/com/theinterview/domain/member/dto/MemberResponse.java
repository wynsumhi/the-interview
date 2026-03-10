package com.theinterview.domain.member.dto;

import com.theinterview.domain.member.Member;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public abstract class MemberResponse {

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class MemberInfoDTO {

        private int memberId;
        private String email;
        private String nickname;
        
        public MemberInfoDTO(Member member) {
            this.memberId = member.getId();
            this.email = member.getEmail();
            this.nickname = member.getNickname();
        }
    }
}
