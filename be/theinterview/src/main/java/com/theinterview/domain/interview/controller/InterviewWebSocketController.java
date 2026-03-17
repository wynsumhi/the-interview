package com.theinterview.domain.interview.controller;

import com.theinterview.domain.interview.dto.InterviewRequest.AudioMessageRequest;
import com.theinterview.domain.interview.dto.InterviewResponse.FollowUpQuestionDTO;
import com.theinterview.domain.interview.facade.InterviewFacade;
import com.theinterview.domain.interview.service.InterviewService;
import java.io.IOException;
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
    private final InterviewService interviewService;
    private final InterviewFacade interviewFacade;

    // 프론트에서 다음 버튼을 누르고 /app/interview/audio 로 음성을 쏘면 여기서 받음
    @MessageMapping("/interview/audio")
    public void receiveAudio(
            @Payload AudioMessageRequest request
    ) throws IOException {
        log.info("수신 데이터 확인 interviewId: {}", request.getInterviewId());

        // Facade 레이어 호출
        FollowUpQuestionDTO response = interviewFacade.processOneQnA(request);

        // 메시지 브로커에게 메시지 전달
        messagingTemplate.convertAndSend("/topic/interview/room/" + request.getInterviewId(),
                response);
    }
}
