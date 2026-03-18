package com.theinterview.domain.interview.entity;


import com.theinterview.domain.interview.enums.InterviewStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;


@Entity
@Table(name = "interview_question")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Column(name = "question_text", nullable = false)
    private String questionText;

    @Column(name = "created_at", nullable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    @Column(name = "status", nullable = false)
    private InterviewStatus status;

    @OneToOne(mappedBy = "interviewQuestion")
    private InterviewAnswer interviewAnswer;

    @Builder
    public InterviewQuestion(Interview interview, Integer sequence, String questionText) {
        this.interview = interview;
        this.sequence = sequence;
        this.questionText = questionText;
        this.status = InterviewStatus.BEFORE_START;
    }

    // 질문 상태 변경 메서드(시작 전 -> 진행 중)
    public void interviewQuestionInProgress() {
        this.status = InterviewStatus.IN_PROGRESS;
    }

    // 질문 상태 변경 메서드(진행중 -> 종료)
    public void interviewQuestionFinished() {
        this.status = InterviewStatus.FINISHED;
    }
}
