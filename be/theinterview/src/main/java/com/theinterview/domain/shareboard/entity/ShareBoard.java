package com.theinterview.domain.shareboard.entity;

import com.theinterview.domain.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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

// 공유 보드 자체를 나타내는 엔티티
// 현재 구현에서는 "한 사용자당 하나의 공유 보드"를 전제로 둔다
@Entity
@Table(name = "share_board")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShareBoard {

    // 공유 보드 PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "share_board_id")
    private Integer id;

    // 공유 보드의 소유자
    // unique = true 를 둬서 한 명의 사용자가 하나의 보드만 갖도록 제한한다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_member_id", nullable = false, unique = true)
    private Member owner;

    // 공개 여부
    // 현재 서비스에는 친구/팔로우 기반 공개 범위가 없으므로 boolean 으로 단순 관리한다
    @Column(name = "share_range", nullable = false)
    private Boolean shareRange;

    // 보드 생성 시각
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 보드 수정 시각
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Builder 생성자
    // 외부에서는 소유자와 공개 여부만 넣어 보드를 생성한다
    @Builder
    public ShareBoard(Member owner, Boolean shareRange) {
        // 보드 소유자 저장
        this.owner = owner;
        // 공개 여부 저장
        this.shareRange = shareRange;
    }
}
