package com.theinterview.domain.interview.controller;

import com.theinterview.domain.interview.dto.InterviewRequest.AudioChunkRequest;
import com.theinterview.domain.interview.dto.InterviewRequest.AudioMessageRequest;
import com.theinterview.domain.interview.dto.InterviewRequest.InterviewExitRequest;
import com.theinterview.domain.interview.dto.InterviewRequest.TotalChunkRequest;
import com.theinterview.domain.interview.dto.InterviewResponse.FollowUpQuestionDTO;
import com.theinterview.domain.interview.facade.InterviewFacade;
import com.theinterview.domain.interview.service.AiUtils;
import com.theinterview.domain.interview.service.InterviewService;
import com.theinterview.global.config.redis.RedisService;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.servlet.View;
import reactor.core.publisher.Mono;

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
        long start = System.currentTimeMillis();
        log.info("요청 감지. 시간 측정 시작");
        // Facade 레이어 호출
        FollowUpQuestionDTO response = interviewFacade.processOneQnA(request);

        log.info("요청 및 반환 종료. 걸린 시간: {}", System.currentTimeMillis() - start);
        // 메시지 브로커에게 메시지 전달
        messagingTemplate.convertAndSend("/topic/interview/room/" + request.getInterviewId(),
                response);

    }

    // 청킹된 오디오 조각이 도착 할 경로
    @MessageMapping("interview/audio/chunk")
    public void receiveChunkAudio(
            @Payload AudioChunkRequest request,
            @Header("simpSessionAttributes") Map<String, Object> sessionAttributes
    ) {

        String email = (String) sessionAttributes.get("email");

        long start = System.currentTimeMillis();
        log.info("청킹된 데이터 도착 interviewId: {}, questionId: {}", request.getInterviewId(),
                request.getQuestionId());
        log.info("요청 감지. 시간 측정 시작");
        // 청크된 오디오 변환을 위해 비동기 STT 모델 호출
        interviewService.chunkAudioSTT(request)
                .flatMap(isLastChunk -> {
                    // 서비스에서 반환환 결과가 true라면 파서드 레이어를 호출하여 꼬리 질문 파이프라인 실행
                    if (isLastChunk) {
                        log.info("모든 청크 조각 도착 완료");
                        // 중복 트리거 방지: 원자적으로 트리거 점유 시도
                        String triggerKey = "interviewId:" + request.getInterviewId()
                                + "questionId:" + request.getQuestionId() + "triggered";
                        if (!redisService.setIfAbsent(triggerKey, "1", Duration.ofMinutes(5))) {
                            log.info("파이프라인 이미 트리거됨 — 청크 경로 중복 실행 방지");
                            return Mono.empty();
                        }
                        // 사용자가 종료를 눌렀는지, 다음을 눌렀는지 분기
                        if (redisService.hasKey(
                                "interviewId:" + request.getInterviewId() + "isExit")) {
                            log.info("인터뷰 종료 로직 실행");
                            // 즉시 DB에 저장 후 종료 Facade 호출
                            return interviewFacade.exitInterviewFacade(email,
                                    InterviewExitRequest.builder()
                                            .interviewId(request.getInterviewId())
                                            .questionId(request.getQuestionId())
                                            .build());
                        } else {
                            log.info("꼬리 질문 생성 로직 실행");
                            // 파서드 레이어 호출 및 다음 질문 변수에 할당
                            return interviewFacade.generateNextQuestionFacade(
                                            request.getInterviewId(),
                                            request.getQuestionId(), start, email)
                                    // 파서드 로직 완료된 후 클라이언트에 전달
                                    .doOnNext(followUpQuestionDTO ->
                                            messagingTemplate.convertAndSend(
                                                    "/topic/interview/tmp/"
                                                            + request.getInterviewId(),
                                                    followUpQuestionDTO));
                        }
                    }
                    // 마지막 청크가 아니라면 아무 행동 하지 않음
                    return Mono.empty();
                })
                .subscribe(
                        null,
                        error -> {
                            log.error("청크 STT 파이프라인 에러 발생 interviewId: {}, 원인: {}",
                                    request.getInterviewId(), error.getMessage());
                            // 재시도를 위한 Redis 상태 초기화
                            String retryRedisKey = "interviewId:" + request.getInterviewId()
                                    + "questionId:" + request.getQuestionId();
                            redisService.deleteValues(retryRedisKey + "triggered");
                            redisService.deleteValues(retryRedisKey + "chunks");
                            redisService.deleteValues(retryRedisKey + "totalChunks");
                            redisService.deleteValues(retryRedisKey + "savedPath");
                            redisService.deleteValues("interviewId:" + request.getInterviewId() + "lock");
                            // 종료 경로에서 saveAnswer가 실행된 경우 고아 InterviewAnswer 삭제
                            interviewService.deleteAnswerByQuestionId(request.getQuestionId());
                            messagingTemplate.convertAndSend(
                                    "/topic/interview/tmp/" + request.getInterviewId(),
                                    (Object) Map.of("type", "STT_ERROR", "message", error.getMessage()));
                        },
                        () -> {
                            // 파이프라인 성공 완료 후 triggered 키 삭제 — 다음 답변/종료 재진입 보장
                            String retryRedisKey = "interviewId:" + request.getInterviewId()
                                    + "questionId:" + request.getQuestionId();
                            redisService.deleteValues(retryRedisKey + "triggered");
                            log.info("파이프라인 완료 — triggered 키 삭제 interviewId: {}",
                                    request.getInterviewId());
                        }
                );

    }

    @MessageMapping("/next")
    public void receiveTotalChunk(
            @Payload TotalChunkRequest request,
            @Header("simpSessionAttributes") Map<String, Object> sessionAttributes
    ) throws IOException {
        String email = (String) sessionAttributes.get("email");
        String redisKey =
                "interviewId:" + request.getInterviewId() + "questionId:" + request.getQuestionId();
        // 저장 요청이 들어오면 redis에 락을 걸어 저장 중 종료를 못하도록 방지함
        redisService.setValues("interviewId:" + request.getInterviewId() + "lock", "lock",
                Duration.ofSeconds(20));
        // 서버 디렉토리에 음성 파일 저장
        String savedPath = interviewService.saveAnswerFile(request.getAnswerAudio(),
                request.getInterviewId());
        // 여기서 바로 총 청크 수를 저장함
        redisService.setValues(redisKey + "totalChunks", request.getTotalChunk(),
                Duration.ofMinutes(3)); // 유효 기간 3분
        // 음성 파일 저장 경로 redis에 저장
        redisService.setValues(redisKey + "savedPath", savedPath, Duration.ofMinutes(3));

        // 레이스 조건 보완: totalChunks 저장 시점에 이미 모든 청크 STT가 완료된 경우 파이프라인 트리거
        int totalChunks = Integer.parseInt(request.getTotalChunk());
        if (totalChunks == redisService.savedChunkCount(redisKey + "chunks")) {
            String triggerKey = redisKey + "triggered";
            if (redisService.setIfAbsent(triggerKey, "1", Duration.ofMinutes(5))) {
                log.info("totalChunks 도착 시점에 모든 청크 완료 확인 — 파이프라인 트리거 (/next 경로)");
                if (redisService.hasKey("interviewId:" + request.getInterviewId() + "isExit")) {
                    log.info("인터뷰 종료 로직 실행 (/next 경로)");
                    interviewFacade.exitInterviewFacade(email,
                            InterviewExitRequest.builder()
                                    .interviewId(request.getInterviewId())
                                    .questionId(request.getQuestionId())
                                    .build()).subscribe(
                            null,
                            error -> {
                                log.error("종료 파이프라인 에러 발생 (/next 경로) interviewId: {}, 원인: {}",
                                        request.getInterviewId(), error.getMessage());
                                // 재시도를 위한 Redis 상태 초기화
                                redisService.deleteValues(redisKey + "triggered");
                                redisService.deleteValues(redisKey + "chunks");
                                redisService.deleteValues(redisKey + "totalChunks");
                                redisService.deleteValues(redisKey + "savedPath");
                                redisService.deleteValues("interviewId:" + request.getInterviewId() + "lock");
                                // 종료 경로에서 saveAnswer가 실행된 경우 고아 InterviewAnswer 삭제
                                interviewService.deleteAnswerByQuestionId(request.getQuestionId());
                                messagingTemplate.convertAndSend(
                                        "/topic/interview/tmp/" + request.getInterviewId(),
                                        (Object) Map.of("type", "STT_ERROR", "message", error.getMessage()));
                            },
                            () -> {
                                // 파이프라인 성공 완료 후 triggered 키 삭제
                                redisService.deleteValues(redisKey + "triggered");
                                log.info("종료 파이프라인 완료 — triggered 키 삭제 (/next 경로) interviewId: {}",
                                        request.getInterviewId());
                            }
                    );
                } else {
                    log.info("꼬리 질문 생성 로직 실행 (/next 경로)");
                    interviewFacade.generateNextQuestionFacade(
                                    request.getInterviewId(),
                                    request.getQuestionId(), System.currentTimeMillis(), email)
                            .doOnNext(followUpQuestionDTO ->
                                    messagingTemplate.convertAndSend(
                                            "/topic/interview/tmp/" + request.getInterviewId(),
                                            followUpQuestionDTO))
                            .subscribe(
                                    null,
                                    error -> {
                                        log.error("꼬리질문 파이프라인 에러 발생 (/next 경로) interviewId: {}, 원인: {}",
                                                request.getInterviewId(), error.getMessage());
                                        // 재시도를 위한 Redis 상태 초기화
                                        redisService.deleteValues(redisKey + "triggered");
                                        redisService.deleteValues(redisKey + "chunks");
                                        redisService.deleteValues(redisKey + "totalChunks");
                                        redisService.deleteValues(redisKey + "savedPath");
                                        redisService.deleteValues("interviewId:" + request.getInterviewId() + "lock");
                                        // 종료 경로에서 saveAnswer가 실행된 경우 고아 InterviewAnswer 삭제
                                        interviewService.deleteAnswerByQuestionId(request.getQuestionId());
                                        messagingTemplate.convertAndSend(
                                                "/topic/interview/tmp/" + request.getInterviewId(),
                                                (Object) Map.of("type", "STT_ERROR", "message", error.getMessage()));
                                    },
                                    () -> {
                                        // 파이프라인 성공 완료 후 triggered 키 삭제
                                        redisService.deleteValues(redisKey + "triggered");
                                        log.info("꼬리질문 파이프라인 완료 — triggered 키 삭제 (/next 경로) interviewId: {}",
                                                request.getInterviewId());
                                    }
                            );
                }
            }
        }
    }
}
