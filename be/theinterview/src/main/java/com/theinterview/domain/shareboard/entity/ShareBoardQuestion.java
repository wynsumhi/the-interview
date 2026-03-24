package com.theinterview.domain.shareboard.entity;

import com.theinterview.domain.interview.entity.Interview;
import com.theinterview.domain.member.Member;
import com.theinterview.domain.shareboard.enums.ShareBoardQuestionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

// 공유 보드의 질문 포스트잇 하나를 나타내는 엔티티
@Entity
@Table(name = "share_board_question")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShareBoardQuestion {

    // 질문 포스트잇 PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "share_board_question_id")
    private Integer id;

    // 이 질문이 속한 공유 보드
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "share_board_id", nullable = false)
    private ShareBoard shareBoard;

    // 질문 작성자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_member_id", nullable = false)
    private Member author;

    // 이 질문으로 시작된 인터뷰
    // 아직 인터뷰 시작 전이면 null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "started_interview_id")
    private Interview startedInterview;

    // 질문 본문
    @Column(name = "content", nullable = false, length = 300)
    private String content;

    // 질문 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ShareBoardQuestionStatus status;

    // 소프트 삭제 여부
    // 실제 레코드는 남기고, 조회에서 제외하는 방식으로 사용한다
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

    // 생성 시각
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 수정 시각
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Builder 생성자
    // 질문 생성 시점에는 아직 인터뷰와 연결되지 않았으므로 OPEN 상태로 시작한다
    @Builder
    public ShareBoardQuestion(ShareBoard shareBoard, Member author, String content) {
        // 질문이 달린 보드 저장
        this.shareBoard = shareBoard;
        // 작성자 저장
        this.author = author;
        // 질문 본문 저장
        this.content = content;
        // 처음 생성된 질문은 사용 전 상태
        this.status = ShareBoardQuestionStatus.OPEN;
        // 처음에는 삭제되지 않은 상태
        this.isDeleted = false;
    }

    // 질문 내용을 수정한다
    public void updateContent(String content) {
        // 수정된 질문 내용 반영
        this.content = content;
    }

    // 이 질문이 인터뷰 시작에 사용되었음을 표시한다
    public void markUsed(Interview startedInterview) {
        // 어떤 인터뷰와 연결되었는지 저장
        this.startedInterview = startedInterview;
        // 상태를 USED 로 변경
        this.status = ShareBoardQuestionStatus.USED;
    }

    // 질문을 삭제 상태로 표시한다
    public void markDeleted() {
        // 소프트 삭제 플래그를 true 로 변경
        this.isDeleted = true;
        // 상태도 DELETED 로 변경
        this.status = ShareBoardQuestionStatus.DELETED;
    }
}
