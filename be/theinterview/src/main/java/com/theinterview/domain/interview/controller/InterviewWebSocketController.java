package com.theinterview.domain.interview.controller;

import com.theinterview.domain.interview.dto.InterviewRequest.AudioMessageRequest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
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
    public void receiveAudio(@Payload AudioMessageRequest request) throws IOException {
        log.info("수신 데이터 확인 roomId: {}", request.getRoomId());

        // 클라이언트에서 보낸 Base64 데이터를 순수 바이트 배열로 변환
        byte[] audioBytes = Base64.getDecoder().decode(request.getAudioData());
        log.info("수신된 파일 크기: {} bytes", audioBytes.length);

        // MVP: 오디오 파일 프로젝트 최상단에 저장
        File dest = new File("test_audio_" + request.getRoomId() + ".webm");
        Files.write(dest.toPath(), audioBytes);
        log.info("음성 파일 저장 완료: {}", dest.getAbsoluteFile());

        // 다시 클라이언트에게 반사(Echo)
        messagingTemplate.convertAndSend("/topic/interview/room/" + request.getRoomId(), request);
    }
}
