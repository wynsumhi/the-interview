package com.theinterview.domain.notification.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public abstract class NotificationResponse {

    // 알림 목록 응답
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class NotificationListDTO {

        private List<NotificationDetail> notifications;
    }

    // 알림 상세
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Builder
    public static class NotificationDetail {

        private Integer notificationId;
        private String message;
        private Integer interviewId;
        private String shareLink;
        private String senderNickname;
        private LocalDateTime createdAt;
    }
}
