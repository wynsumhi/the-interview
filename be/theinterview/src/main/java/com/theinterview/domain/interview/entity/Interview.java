package com.theinterview.domain.interview.entity;

import com.theinterview.domain.interview.enums.InterviewStatus;
import com.theinterview.domain.member.Member;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "interview")
public class Interview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "interview_id")
    private Integer id;

    // Member 엔티티와 연관관계 연결
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "title", length = 300, nullable = false)
    private String title;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private InterviewStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "share_range", nullable = false)
    private Boolean shareRange;

    @Column(name = "share_link", length = 300)
    private String shareLink;

    @OneToMany(mappedBy = "interview")
    @OrderBy("sequence ASC") // 시퀀스 순서대로 오름차순으로 가져옴
    private List<InterviewQuestion> interviewQuestions;

    @Builder
    public Interview(Member member, String title, InterviewStatus status,
            Boolean shareRange) {
        this.member = member;
        this.title = title;
        this.status = status;
        this.shareRange = shareRange;
    }

    /**
     * 인터뷰 상태 전환 메서드 (시작 전 -> 진행 중)
     */
    public void interviewInProgress() {
        this.status = InterviewStatus.IN_PROGRESS;
    }

    /**
     * 인터뷰 상태 전환 메서드 (진행중 -> 완료) 및 제목 수정
     */
    public void interviewFinished(String title) {
        this.status = InterviewStatus.FINISHED;
        this.title = title;
    }

    // 인터뷰 공개범위 수정
    public void setShareRange(Boolean isShared) {
        this.shareRange = isShared;

        if (isShared) {
            this.shareLink = java.util.UUID.randomUUID().toString();
        } else {
            this.shareLink = null;
        }
    }
}
