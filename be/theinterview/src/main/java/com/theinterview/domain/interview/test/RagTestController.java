package com.theinterview.domain.interview.test;

import com.theinterview.domain.interview.dto.AiRagDto;
import com.theinterview.domain.interview.service.AiUtils;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/test/rag")
@RequiredArgsConstructor
public class RagTestController {

    private final AiUtils aiUtils;

    @PostMapping
    public ResponseEntity<String> testRagSequence() {
        log.info("=== 🧪 RAG 파이프라인 통신 순차 테스트 시작 ===");

        // 1. 공통 더미 데이터 생성
        String dummyUserId = "user-" + UUID.randomUUID().toString().substring(0, 8);
        String dummyInterviewId = "interview-" + UUID.randomUUID().toString().substring(0, 8);

        AiRagDto.Turn dummyTurn = AiRagDto.Turn.builder()
                .questionId("q-001")
                .answerId("a-001")
                .question("최근 작업 중 가장 애착이 가는 프로젝트는 무엇인가요?")
                .answer("오래 준비한 독립 잡지 리디자인 프로젝트가 가장 기억에 남아요.")
                .turnIndex(1)
                .metadata(Map.of("title", "테스트 인터뷰 턴"))
                .build();

        AiRagDto.Interview dummyInterview = AiRagDto.Interview.builder()
                .interviewId(dummyInterviewId)
                .turns(List.of(dummyTurn))
                .metadata(Map.of("title", "테스트 인터뷰 전체"))
                .build();

        try {
            // =================================================================
            // [STEP 1] Reindex 테스트
            // =================================================================
            log.info("[STEP 1] Reindex API 호출 테스트 진행 중...");
            AiRagDto.ReindexRequest reindexReq = AiRagDto.ReindexRequest.builder()
                    .userId(dummyUserId)
                    .interviews(List.of(dummyInterview))
                    .build();

            // 테스트 컨트롤러이므로 순차 확인을 위해 의도적으로 .block() 사용
            AiRagDto.ReindexResponse reindexRes = aiUtils.callFastApiForRagReindex(reindexReq)
                    .block();
            log.info("✅ Reindex 결과 성공: {}", reindexRes.getMessage());

            // =================================================================
            // [STEP 2] Upsert 테스트
            // =================================================================
            log.info("[STEP 2] Upsert API 호출 테스트 진행 중...");
            AiRagDto.UpsertRequest upsertReq = AiRagDto.UpsertRequest.builder()
                    .userId(dummyUserId)
                    .interviewId(dummyInterviewId)
                    .turns(List.of(dummyTurn))
                    .metadata(Map.of("title", "추가 적재 인터뷰"))
                    .build();

            AiRagDto.UpsertResponse upsertRes = aiUtils.callFastApiForRagUpsert(upsertReq).block();
            log.info("✅ Upsert 결과 성공: {}", upsertRes.getMessage());

            // =================================================================
            // [STEP 3] Delete 테스트
            // =================================================================
            log.info("[STEP 3] Delete API 호출 테스트 진행 중...");
            AiRagDto.DeleteRequest deleteReq = AiRagDto.DeleteRequest.builder()
                    .userId(dummyUserId)
                    .interviewId(dummyInterviewId)
                    .build();

            AiRagDto.DeleteResponse deleteRes = aiUtils.callFastApiForRagDelete(deleteReq).block();
            log.info("✅ Delete 결과 성공: {}", deleteRes.getMessage());

            log.info("=== 🧪 RAG 파이프라인 통신 순차 테스트 완료 ===");
            return ResponseEntity.ok("테스트가 성공적으로 완료되었습니다. 인텔리제이 콘솔 로그를 확인하세요.");

        } catch (Exception e) {
            log.error("🚨 RAG 파이프라인 테스트 중 에러 발생: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("테스트 실패: " + e.getMessage());
        }
    }
}
