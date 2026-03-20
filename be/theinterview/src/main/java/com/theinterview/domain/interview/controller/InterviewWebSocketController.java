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

        // 청크된 오디오 변환을 위해 비동기 STT 모델 호출
        interviewService.chunkAudioSTT(request)
                .subscribe(isLastChunk -> {
                    // 서비스에서 반환환 결과가 true라면 파서드 레이어를 호출하여 꼬리 질문 파이프라인 실행
                    if (isLastChunk) {
                        log.info("모든 청크 조각 도착 완료");
                        // 파서드 레이어 호출 및 다음 질문 변수에 할당
                        String nextQuestion = interviewFacade.generateNextQuestionFacade(
                                request.getInterviewId(),
                                request.getQuestionId());
                        // todo: 다음 질문만 string으로 넘기는 로직 -> DTO로 감싸서 필요한 데이터들을 모두 보내주는 로직으로 변환(엔드포인트 수정)
                        messagingTemplate.convertAndSend(
                                "/topic/interview/tmp/" + request.getInterviewId(), nextQuestion);

                    }
                });
    }

    @MessageMapping("/next")
    public void receiveTotalChunk(
            @Payload TotalChunkRequest request
    ) throws IOException {
        String redisKey =
                "interviewId:" + request.getInterviewId() + "questionId:" + request.getQuestionId();
        // 서버 디렉토리에 음성 파일 저장
        String savedPath = interviewService.saveAnswerFile(request.getAnswerAudio(),
                request.getInterviewId());
        // 여기서 바로 총 청크 수를 저장함
        redisService.setValues(redisKey + "totalChunks", request.getTotalChunk(),
                Duration.ofMinutes(3)); // 유효 기간 3분
        // 음성 파일 저장 경로 redis에 저장
        redisService.setValues(redisKey + "savedPath", savedPath, Duration.ofMinutes(3));

    }
}
