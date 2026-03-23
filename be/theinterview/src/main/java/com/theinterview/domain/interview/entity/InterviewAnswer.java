package com.theinterview.domain.interview.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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
@Table(name = "interview_answer")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "qna_id", nullable = false)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private InterviewQuestion interviewQuestion;

    @Column(name = "answer_text", nullable = false, length = 300)
    private String answerText;

    @Column(name = "answer_audio_url", nullable = false, length = 500)
    private String answerAudioUrl;

    @Column(name = "share_range", nullable = false)
    private Boolean shareRange = false;

    @Column(name = "answer_keyword", length = 300)
    private String answerKeyword;

    @Column(name = "created_at", nullable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    @Builder
    public InterviewAnswer(InterviewQuestion interviewQuestion, String answerText,
            String answerAudioUrl, Boolean shareRange) {
        this.interviewQuestion = interviewQuestion;
        this.answerText = answerText;
        this.answerAudioUrl = answerAudioUrl;
        this.shareRange = (shareRange != null) ? shareRange : false;
    }

    // 답변 공유 범위 수정
    public void updateShareRange(Boolean shareRange) {
        this.shareRange = shareRange;
    }
}
