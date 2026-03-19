package com.theinterview.domain.interview.test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TestWebSocketController {

    private final TestSttService testSttService;

    // 단순 StringBuilder 대신, 상태 관리 객체(SttSessionState)를 저장
    private final Map<String, SttSessionState> sessionStates = new ConcurrentHashMap<>();

    // 프론트가 JSON(TestAudioChunkDto)으로 보냄
    @MessageMapping("/test/audio-chunk")
    public void receiveAudioChunk(TestAudioChunkDto request,
            SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();

        // 세션 상태 객체 가져오기 (없으면 생성)
        SttSessionState state = sessionStates.computeIfAbsent(sessionId,
                k -> new SttSessionState());

        byte[] audioBytes = java.util.Base64.getDecoder().decode(request.getAudioData());
        log.info("📥 [청크 수신] 세션: {}, Index: {}, 크기: {} bytes", sessionId, request.getIndex(),
                audioBytes.length);

        // ⭐️ 진정한 비동기 병렬 처리: 수신 스레드는 즉시 반환하고 백그라운드 스레드가 AI 서버 호출
        CompletableFuture.runAsync(() -> {
            // STT 호출 (FastAPI)
            String sttText = testSttService.callSttApi(audioBytes);
            log.info("🔥 [STT 완료] Index: {}, 결과: {}", request.getIndex(), sttText);

            // 상태 객체에 결과 저장 및 카운트 증가
            state.addResult(request.getIndex(), sttText);

            // 만약 내가 마지막으로 처리된 청크라면? 최종 결과 출력!
            if (state.isAllProcessed()) {
                printFinalResult(sessionId, state);
            }
        });
    }

    // 프론트가 JSON(TestAudioEndDto)으로 보냄
    @MessageMapping("/test/audio-end")
    public void finishAudioStream(TestAudioEndDto request,
            SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.info("🛑 [종료 신호 수신] 세션: {}, 총 청크 개수: {}", sessionId, request.getTotalCount());

        SttSessionState state = sessionStates.get(sessionId);
        if (state != null) {
            // 총 개수와 현재 시간(EOF 수신 시간) 세팅
            state.markAsFinished(request.getTotalCount());

            // STT 처리가 이미 다 끝난 상태에서 종료 신호가 뒤늦게 왔다면? 여기서 즉시 출력!
            if (state.isAllProcessed()) {
                printFinalResult(sessionId, state);
            }
        }
    }

    private void printFinalResult(String sessionId, SttSessionState state) {
        // Map에서 삭제하여 메모리 누수 방지
        sessionStates.remove(sessionId);

        log.info("=============================================");
        log.info("🎯 [최종 STT 조립 완료] 세션: {}", sessionId);
        log.info(state.getFinalResultAndLatency());
        log.info("=============================================");
    }
}