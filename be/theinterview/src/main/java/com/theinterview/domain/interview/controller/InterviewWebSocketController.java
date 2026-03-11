package com.theinterview.domain.interview.controller;

import com.theinterview.domain.interview.dto.InterviewRequest.AudioMessageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class InterviewWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    // 프론트에서 다음 버튼을 누르고 /app/interview/audio 로 음성을 쏘면 여기서 받음
    @MessageMapping("/interview/audio")
    public void receiveAudio(@Payload AudioMessageRequest request) {
        log.info("수신 데이터 확인: {}", request.getRoomId());

        // 다시 클라이언트에게 반사(Echo)
        messagingTemplate.convertAndSend("/topic/interview/room/" + request.getRoomId(), request);
    }
}
