package com.theinterview.domain.notification.entity;

import com.theinterview.domain.interview.entity.Interview;
import com.theinterview.domain.member.Member;
import com.theinterview.domain.shareboard.entity.ShareBoardQuestion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Integer id;

    // 알림을 받는 사람
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private Member receiver;

    // 인터뷰 진행한 사람
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private Member sender;

    // 생성된 인터뷰
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;

    // 원본 질문
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "share_board_question_id")
    private ShareBoardQuestion shareBoardQuestion;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "created_at", nullable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    public Notification(Member receiver, Member sender, Interview interview,
            ShareBoardQuestion question, String message) {
        this.receiver = receiver;
        this.sender = sender;
        this.interview = interview;
        this.shareBoardQuestion = question;
        this.message = message;
    }
}
