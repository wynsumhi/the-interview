package com.theinterview.domain.shareboard.entity;

import com.theinterview.domain.shareboard.enums.ShareBoardPostitType;
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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "share_board_postit_layout",
        uniqueConstraints = @UniqueConstraint(columnNames = {
                "share_board_id", "postit_type", "source_id"
        }))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShareBoardPostitLayout {

    // 포스트잇 레이아웃 PK입니다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "share_board_postit_layout_id")
    private Long id;

    // 어느 공유 보드에 속한 레이아웃인지 연결합니다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "share_board_id", nullable = false)
    private ShareBoard shareBoard;

    // 원본 포스트잇 타입이 BOOKMARK인지 QUESTION인지 저장합니다.
    @Enumerated(EnumType.STRING)
    @Column(name = "postit_type", nullable = false, length = 30)
    private ShareBoardPostitType postitType;

    // 원본 데이터의 PK를 저장합니다.
    @Column(name = "source_id", nullable = false)
    private Integer sourceId;

    // 포스트잇 색상은 시스템이 지정하므로 레이아웃에 함께 저장합니다.
    @Column(name = "color_code", nullable = false, length = 20)
    private String colorCode;

    // 포스트잇의 기울기 값을 저장합니다.
    @Column(name = "rotation_deg", nullable = false, precision = 5, scale = 2)
    private BigDecimal rotationDeg;

    // 프론트가 계산한 섹션 내부 X 좌표를 저장합니다.
    @Column(name = "position_x", nullable = false, precision = 7, scale = 4)
    private BigDecimal positionX;

    // 프론트가 계산한 섹션 내부 Y 좌표를 저장합니다.
    @Column(name = "position_y", nullable = false, precision = 7, scale = 4)
    private BigDecimal positionY;

    // 같은 섹션 안에서 겹칠 때 우선순위를 저장합니다.
    @Column(name = "z_index", nullable = false)
    private Integer zIndex;

    // 레이아웃이 처음 생성된 시각입니다.
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 레이아웃이 마지막으로 수정된 시각입니다.
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public ShareBoardPostitLayout(ShareBoard shareBoard, ShareBoardPostitType postitType,
            Integer sourceId, String colorCode, BigDecimal rotationDeg, BigDecimal positionX,
            BigDecimal positionY, Integer zIndex) {
        // 원본 포스트잇 데이터와 화면 배치 정보를 분리해서 저장합니다.
        this.shareBoard = shareBoard;
        this.postitType = postitType;
        this.sourceId = sourceId;
        this.colorCode = colorCode;
        this.rotationDeg = rotationDeg;
        this.positionX = positionX;
        this.positionY = positionY;
        this.zIndex = zIndex;
    }

    public void updatePlacement(BigDecimal rotationDeg, BigDecimal positionX, BigDecimal positionY,
            Integer zIndex) {
        // 색상은 고정값이므로 위치와 회전 정보만 수정합니다.
        this.rotationDeg = rotationDeg;
        this.positionX = positionX;
        this.positionY = positionY;
        this.zIndex = zIndex;
    }
}
