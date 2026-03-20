package com.theinterview.domain.interview.controller;

import com.theinterview.domain.interview.dto.InterviewRequest.AudioChunkRequest;
import com.theinterview.domain.interview.dto.InterviewRequest.AudioMessageRequest;
import com.theinterview.domain.interview.dto.InterviewRequest.TotalChunkRequest;
import com.theinterview.domain.interview.dto.InterviewResponse.FollowUpQuestionDTO;
import com.theinterview.domain.interview.facade.InterviewFacade;
import com.theinterview.domain.interview.service.AiUtils;
import com.theinterview.domain.interview.service.InterviewService;
import com.theinterview.global.config.redis.RedisService;
import java.io.IOException;
import java.time.Duration;
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
            @Payload AudioChunkRequest request
    ) {
        log.info("청킹된 데이터 도착 interviewId: {}, questionId: {}", request.getInterviewId(),
                request.getQuestionId());

        // 비동기 청킹 요청 및 구독을 통해 해당 메서드에 대한 return이 발생하면 여기서 잡아, redis 저장 로직을 수행함
        aiUtils.callFastApiForSttChunk(request).subscribe(sttChunkResponse -> {
                    // redis 키 저장
                    String redisKey = "interviewId:" + request.getInterviewId() + "questionId:"
                            + request.getQuestionId();
                    // redis에 청크된 STT 결과, 인덱스 저장
                    redisService.addChunkToSortedSet(
                            redisKey + "chunks",
                            sttChunkResponse.getChunkText(),
                            request.getChuckIndex()
                    );
                    log.info("청킹된 오디오 조각 STT 변환 성공 및 redis에 저장 저장된 텍스트: {}",
                            sttChunkResponse.getChunkText());

                    // 청킹이 완료되었다면 해당 질문이 다음 버튼이 눌렸는지 redis에 키를 조회해서 확인함
                    if (redisService.hasKey(redisKey + "totalChunks")) {
                        // redis에 저장되어 있는 총 청크 수를 조회함
                        int totalChunks = Integer.parseInt(
                                redisService.getValues(redisKey + "totalChunks"));
                        // 만약 같다면 종료 처리 가능
                        if (totalChunks == redisService.savedChunkCount(redisKey + "chunks")) {
                            // 여기서 꼬리 질문 생성 메서드 호출
                            log.info("총 청크 수, 변환된 청크 수 일치 확인. 꼬리 질문 생성 로직 호출");
                        }
                    }
                },
                error -> {
                    log.error("STT 변환 중 에러 발생: {}", error.getMessage());
                });

    }

    @MessageMapping("/next")
    public void receiveTotalChunk(
            @Payload TotalChunkRequest request
    ) {
        String redisKey =
                "interviewId:" + request.getInterviewId() + "questionId:" + request.getQuestionId();
        // 여기서 바로 총 청크 수를 저장함
        redisService.setValues(redisKey + "totalChunks", request.getTotalChunk(),
                Duration.ofMinutes(3)); // 유효 기간 3분
    }
}
