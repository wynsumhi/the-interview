package com.theinterview.domain.interview.dto;

import com.theinterview.domain.interview.dto.AiRagDto.Turn;
import com.theinterview.domain.member.Member;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public abstract class FacadeInnerDto {

    // 퍼사드에서 데이터를 받아 올 목적으로 사용하는 내부 dto
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class AnswerTts {

        private Member member; // 일단 객체 자체로 받고 구현하면서 정말 필요한 값이 확정되면 변경
        private String QnACouple; // QnA 쌍
        private List<Turn> turns;

        public void protectEmptyString() {
            this.QnACouple = "저장된 텍스트가 없습니다. 아무 질문을 반환하세요.";
        }
    }
}
