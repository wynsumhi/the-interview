package com.theinterview.domain.shareboard.enums;

// 공유 보드 질문 포스트잇의 상태를 표현하는 enum
public enum ShareBoardQuestionStatus {
    // 아직 인터뷰 시작에 사용되지 않은 일반 질문 상태
    OPEN,
    // 보드 주인이 이 질문으로 인터뷰를 실제 시작한 상태
    USED,
    // 삭제 처리된 상태
    DELETED
}
