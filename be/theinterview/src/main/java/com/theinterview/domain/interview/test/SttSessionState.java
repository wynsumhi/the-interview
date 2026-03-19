package com.theinterview.domain.interview.test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;

@Getter
public class SttSessionState {

    // 순서 보장을 위해 index를 Key로, STT 결과를 Value로 저장
    private final Map<Integer, String> chunkResults = new ConcurrentHashMap<>();

    // 비동기 환경에서 안전하게 숫자를 세기 위한 원자적(Atomic) 변수
    private final AtomicInteger processedCount = new AtomicInteger(0);

    // 프론트에서 전송할 총 청크 개수 (-1은 아직 종료 신호가 안 왔음을 의미)
    private volatile int totalCount = -1;

    // 지연 시간(Latency) 측정을 위한 타임스탬프
    private volatile Instant eofReceivedTime;

    // 1. 청크 STT 완료 시 결과 저장 및 카운트 증가
    public void addResult(int index, String result) {
        chunkResults.put(index, result);
        processedCount.incrementAndGet();
    }

    // 2. 클라이언트의 종료 신호(EOF) 도달 시 총 개수 및 시간 기록
    public void markAsFinished(int totalCount) {
        this.totalCount = totalCount;
        this.eofReceivedTime = Instant.now(); // 종료 버튼 누른 시점 기록
    }

    // 3. 모든 처리가 끝났는지 확인 (총 개수가 설정되었고, 처리된 개수와 일치하는가?)
    public boolean isAllProcessed() {
        return totalCount != -1 && processedCount.get() == totalCount;
    }

    // 4. 최종 결과 조립 및 지연 시간 반환
    public String getFinalResultAndLatency() {
        StringBuilder sb = new StringBuilder();
        // Index 순서대로 조립
        for (int i = 0; i < totalCount; i++) {
            sb.append(chunkResults.getOrDefault(i, "[유실됨]")).append(" ");
        }

        // 지연 시간 계산 (마지막 청크 완료 시점 - 프론트엔드 종료 버튼 클릭 시점)
        long latencyMs = Duration.between(eofReceivedTime, Instant.now()).toMillis();

        return String.format("⏱️ [지연 시간: %d ms]\n📝 [최종 문장]: %s", latencyMs, sb.toString().trim());
    }
}
