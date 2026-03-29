package com.theinterview.domain.notification.controller;

import com.theinterview.domain.notification.dto.NotificationResponse.NotificationListDTO;
import com.theinterview.domain.notification.service.NotificationService;
import com.theinterview.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/notification")
public class NotificationController {

    private final NotificationService notificationService;

    // 알림 목록 조회
    @GetMapping
    public ApiResponse<NotificationListDTO> getNotifications(
            @AuthenticationPrincipal User user) {
        return ApiResponse.onSuccess(
                notificationService.getNotifications(user.getUsername())
        );
    }

    // 알림 클릭 시 삭제
    @DeleteMapping("/{notificationId}")
    public ApiResponse<Void> deleteNotification(
            @PathVariable Integer notificationId,
            @AuthenticationPrincipal User user) {
        notificationService.deleteNotification(notificationId, user.getUsername());
        return ApiResponse.OK;
    }
}
