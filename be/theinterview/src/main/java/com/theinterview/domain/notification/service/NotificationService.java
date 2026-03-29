package com.theinterview.domain.notification.service;

import com.theinterview.domain.interview.entity.Interview;
import com.theinterview.domain.member.Member;
import com.theinterview.domain.member.repository.MemberRepository;
import com.theinterview.domain.notification.dto.NotificationResponse.NotificationDetail;
import com.theinterview.domain.notification.dto.NotificationResponse.NotificationListDTO;
import com.theinterview.domain.notification.entity.Notification;
import com.theinterview.domain.notification.repository.NotificationRepository;
import com.theinterview.domain.shareboard.entity.ShareBoardQuestion;
import com.theinterview.global.response.exception.GeneralException;
import com.theinterview.global.response.status.ErrorStatus;
import jakarta.transaction.Transactional;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;

    // 알림 생성
    @Transactional
    public void createNotification(Member receiver, Member sender,
            Interview interview, ShareBoardQuestion question) {
        String message = sender.getNickname() + " 님에게 남긴 질문에 대한 인터뷰가 생성되었습니다.";

        Notification notification = new Notification(
                receiver, sender, interview, question, message
        );
        notificationRepository.save(notification);
    }

    // 알림 목록 조회
    public NotificationListDTO getNotifications(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));

        List<Notification> notifications = notificationRepository
                .findAllByReceiverId(member.getId());

        List<NotificationDetail> details = notifications.stream()
                .map(n -> NotificationDetail.builder()
                        .notificationId(n.getId())
                        .message(n.getMessage())
                        .interviewId(n.getInterview().getId())
                        .shareLink(n.getInterview().getShareLink())
                        .senderNickname(n.getSender().getNickname())
                        .createdAt(n.getCreatedAt())
                        .build())
                .toList();

        return NotificationListDTO.builder()
                .notifications(details)
                .build();
    }

    // 알림 클릭시 삭제
    @Transactional
    public void deleteNotification(Integer notificationId, String email) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.NOTIFICATION_NOT_FOUND));

        if (!notification.getReceiver().getEmail().equals(email)) {
            throw new GeneralException(ErrorStatus.FORBIDDEN);
        }

        notificationRepository.delete(notification);
    }
}
