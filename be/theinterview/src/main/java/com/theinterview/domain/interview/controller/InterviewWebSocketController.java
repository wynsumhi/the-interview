package com.theinterview.domain.interview.controller;

import com.theinterview.domain.interview.dto.InterviewRequest.AudioChuckRequest;
import com.theinterview.domain.interview.dto.InterviewRequest.AudioMessageRequest;
import com.theinterview.domain.interview.dto.InterviewResponse.FollowUpQuestionDTO;
import com.theinterview.domain.interview.facade.InterviewFacade;
import com.theinterview.domain.interview.service.AiUtils;
import com.theinterview.domain.interview.service.InterviewService;
import com.theinterview.global.config.redis.RedisService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.servlet.View;

@Slf4j
@Controller
@RequiredArgsConstructor
public class InterviewWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final InterviewService interviewService;
    private final InterviewFacade interviewFacade;
    private final AiUtils aiUtils;
    private final RedisService redisService;
    private final View error;

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

    // 청킹된 오디오 조각이 도착 할 경로
    @MessageMapping("interview/audio/chunk")
    public void receiveChunkAudio(
            @Payload AudioChuckRequest request
    ) {
        log.info("청킹된 데이터 도착 interviewId: {}, questionId: {}", request.getInterviewId(),
                request.getQuestionId());

        // 비동기 청킹 요청 및 구독을 통해 해당 메서드에 대한 return이 발생하면 여기서 잡아, redis 저장 로직을 수행함
        aiUtils.callFastApiForSttChunk(request).subscribe(sttChunkResponse -> {
                    redisService.addChunkToSortedSet(
                            "interviewId:" + request.getInterviewId() + "questionId:"
                                    + request.getQuestionId() + "chunks",
                            sttChunkResponse.getChunkText(),
                            request.getChuckIndex()
                    );
                    log.info("청킹된 오디오 조각 STT 변환 성공 및 redis에 저장 저장된 텍스트: {}",
                            sttChunkResponse.getChunkText());
                },
                error -> {
                    log.error("STT 변환 중 에러 발생: {}", error.getMessage());
                });

    }
}
