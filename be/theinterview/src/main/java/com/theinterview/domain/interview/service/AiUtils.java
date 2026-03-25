package com.theinterview.domain.interview.service;

import com.theinterview.domain.interview.dto.AiRagDto.DeleteRequest;
import com.theinterview.domain.interview.dto.AiRagDto.DeleteResponse;
import com.theinterview.domain.interview.dto.AiRagDto.ReindexRequest;
import com.theinterview.domain.interview.dto.AiRagDto.ReindexResponse;
import com.theinterview.domain.interview.dto.AiRagDto.UpsertRequest;
import com.theinterview.domain.interview.dto.AiRagDto.UpsertResponse;
import com.theinterview.domain.interview.dto.FastApiRequest.AiNextOpeningRequest;
import com.theinterview.domain.interview.dto.FastApiRequest.AiStartTtsRequest;
import com.theinterview.domain.interview.dto.FastApiRequest.AiTitleRequest;
import com.theinterview.domain.interview.dto.FastApiRequest.KeywordRequest;
import com.theinterview.domain.interview.dto.FastApiResponse.AiNextOpeningResponse;
import com.theinterview.domain.interview.dto.FastApiResponse.AiPipelineResponse;
import com.theinterview.domain.interview.dto.FastApiResponse.AiStartTtsResponse;
import com.theinterview.domain.interview.dto.FastApiResponse.AiTitleResponse;
import com.theinterview.domain.interview.dto.FastApiResponse.KeywordResponse;
import com.theinterview.domain.interview.dto.InterviewRequest.AudioChunkRequest;
import com.theinterview.domain.interview.dto.InterviewResponse.SttChunkResponse;
import com.theinterview.global.response.exception.GeneralException;
import com.theinterview.global.response.status.ErrorStatus;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;


// todo: 모든 block으로 작동하는 webClient 수정 필요
@Slf4j
@Component
@RequiredArgsConstructor
public class AiUtils {

    private final WebClient webClient;

    // 클라이언트에서 청킹해서 보내는 audio 조각을 STT로 변환만을 담당하는 메서드
    public Mono<SttChunkResponse> callFastApiForSttChunk(AudioChunkRequest request) {

        // multipart/form-data 생성을 위한 빌더
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

        // 오디오 파일 세팅(.webm)
        ByteArrayResource audioResource = new ByteArrayResource(request.getAudioData()) {
            @Override
            public String getFilename() {
                return "answer.webm";
            }
        };
        bodyBuilder.part("audio_file", audioResource)
                .contentType(MediaType.parseMediaType("audio/webm"));

        log.info("청킹된 오디오 파일 STT 변환을 위한 통신 시작");

        // WbFlux 비동기 체인 시작
        return webClient.post()
                .uri("api/v1/ai/stt")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve() // 요청을 비동기적으로 쓴다는 선언
                // 비동기 환경에서는 try-catch가 작동하지 않음, 체인 내부에서 예외를 잡아 처리함
                .onStatus(status -> status.is4xxClientError(), clientResponse -> {
                    log.error("FastAPI 4xx 에러 발생");
                    // Mono.error를 통해 비동기 스트림에 에러 발생 신호를 보냄
                    return Mono.error(new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR));
                })
                .onStatus(status -> status.is5xxServerError(), clientResponse -> {
                    log.error("FastAPI 5xx 에러 발생");
                    return Mono.error(new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR));
                })
                // Json 응답을 DTO로 변환하겠다는 약속만 넘기고 메서드를 즉시 종료함
                .bodyToMono(SttChunkResponse.class);

    }

    // fast api를 호출하여 꼬리질문 관련 파이프라인을 거치는 헬퍼 메서드 -> STT 청크 로직에서 사용됨
    // todo: 지금은 감정을 받지 않도록 설계됨. 감정 로직이 확실해지면 추가
    public Mono<AiPipelineResponse> callFastApiForNextQuestionV2(
            byte[] audioBytes,
            int sequence,
            String nickname,
            String previousQuestion,
            Boolean isFirstFinished,
            String sttText,
            String voice) {

        // multipart/form-data 생성을 위한 빌더
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

        // 1. 오디오 파일 셋팅 (.webm 강제 주입)
        ByteArrayResource audioResource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return "answer.webm";
            }
        };
        bodyBuilder.part("audio_file", audioResource)
                .contentType(MediaType.parseMediaType("audio/webm"));

        // 2. MVP 기준 필수 파라미터 세팅 (항상 false)
        bodyBuilder.part("is_first_finished", isFirstFinished);

        // 3. is_first_finished=false 일 때 권장/필수 파라미터 세팅
        bodyBuilder.part("sequence", sequence);
        bodyBuilder.part("nickname", nickname);

        // 4. sequence >= 2 일 경우 이전 질문 텍스트 세팅
        if (sequence >= 2 && previousQuestion != null) {
            bodyBuilder.part("previous_question", previousQuestion);
        }

        // 5. 조립된 사용자 답변 텍스트 세팅
        bodyBuilder.part("stt_text", sttText);
        if (voice != null && !voice.isBlank()) {
            bodyBuilder.part("voice", voice);
        }

        log.info("AI 서버와 통신 시작 요청 URI: {}", "/api/v1/ai/interview/pipeline/after-stt");

        // 비동기 webclient를 이용한 post 요청
        return webClient.post()
                .uri("/api/v1/ai/interview/pipeline/after-stt")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                // 명세서에 명시된 400 Bad Request (잘못된 파일 형식 등) 방어 로직 추가
                .onStatus(status -> status.is4xxClientError(), clientResponse -> {
                    log.error("FastAPI 클라이언트 에러 발생 (4xx): {}", clientResponse.statusCode());
                    return Mono.error(new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR));
                })
                .onStatus(status -> status.is5xxServerError(), clientResponse -> {
                    log.error("FastAPI 서버 에러 발생 (5xx): {}", clientResponse.statusCode());
                    return Mono.error(new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR));
                })
                .bodyToMono(AiPipelineResponse.class);
    }

    // fast api를 호출하여 꼬리질문 관련 파이프라인을 거치는 헬퍼 메서드
    public AiPipelineResponse callFastApiForNextQuestion(
            byte[] audioBytes,
            int sequence,
            String nickname,
            String previousQuestion,
            Boolean isFirstFinished,
            String voice) {

        // multipart/form-data 생성을 위한 빌더
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

        // 1. 오디오 파일 셋팅 (.webm 강제 주입)
        ByteArrayResource audioResource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return "answer.webm";
            }
        };
        bodyBuilder.part("audio_file", audioResource)
                .contentType(MediaType.parseMediaType("audio/webm"));

        // 2. MVP 기준 필수 파라미터 세팅 (항상 false)
        bodyBuilder.part("is_first_finished", isFirstFinished);

        // 3. is_first_finished=false 일 때 권장/필수 파라미터 세팅
        bodyBuilder.part("sequence", sequence);
        bodyBuilder.part("nickname", nickname);

        // 4. sequence >= 2 일 경우 이전 질문 텍스트 세팅
        if (sequence >= 2 && previousQuestion != null) {
            bodyBuilder.part("previous_question", previousQuestion);
        }
        if (voice != null && !voice.isBlank()) {
            bodyBuilder.part("voice", voice);
        }

        log.info("AI 서버와 통신 시작 요청 URI: {}", "/api/v1/ai/interview/pipeline");

        // 비동기 webclient를 이용한 post 요청
        return webClient.post()
                .uri("/api/v1/ai/interview/pipeline")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                // 명세서에 명시된 400 Bad Request (잘못된 파일 형식 등) 방어 로직 추가
                .onStatus(status -> status.is4xxClientError(), clientResponse -> {
                    log.error("FastAPI 클라이언트 에러 발생 (4xx): {}", clientResponse.statusCode());
                    return Mono.error(new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR));
                })
                .onStatus(status -> status.is5xxServerError(), clientResponse -> {
                    log.error("FastAPI 서버 에러 발생 (5xx): {}", clientResponse.statusCode());
                    return Mono.error(new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR));
                })
                .bodyToMono(AiPipelineResponse.class)
                .block();
    }

    // 인터뷰 첫 시작 질문 TTS 반환을 위해 AI 서버 호출 메서드
    public Mono<AiStartTtsResponse> callFastApiForStartTts(AiStartTtsRequest request
    ) {

        log.info("AI 서버와 TTS 통신 시작 uri: {}", "/api/v1/ai/interview/start-tts");
        return webClient.post()
                .uri("/api/v1/ai/interview/start-tts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AiStartTtsResponse.class)
                .doOnNext(aiStartTtsResponse -> log.info("AI 서버와 TTS 통신 완료. 반환된 텍스트: {}",
                        aiStartTtsResponse.getQuestionText()))
                .doOnError(e -> log.error("TTS 통신 실패: {}", e.getMessage()));
    }

    // 종료를 위해 AI 서버를 호출하여 제목 요약
    public AiTitleResponse callFastApiForInterviewTitle(AiTitleRequest request) {
        log.info("제목 생성 요청 시작");

        return webClient.post()
                .uri("/api/v1/ai/interview/title")
                .bodyValue(request)
                .retrieve()
                // 4xx 에러 처리 (문서에 명시된 422 에러 등)
                .onStatus(status -> status.is4xxClientError(), clientResponse -> {
                    log.error("FastAPI 클라이언트 에러 발생 (4xx): {}", clientResponse.statusCode());
                    // ErrorStatus.FAST_API_ERROR 등 본인 프로젝트에 맞는 에러 코드로 수정하세요
                    return Mono.error(new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR));
                })
                // 5xx 에러 처리 (FastAPI 서버가 죽었거나 모델에서 에러 났을 때)
                .onStatus(status -> status.is5xxServerError(), clientResponse -> {
                    log.error("FastAPI 서버 에러 발생 (5xx): {}", clientResponse.statusCode());
                    return Mono.error(new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR));
                })
                // JSON 응답을 AiTitleResponse 객체로 역직렬화
                .bodyToMono(AiTitleResponse.class)
                // 현재 서비스가 동기식(@Transactional)으로 돌아가고 있으므로 block()으로 결과를 기다림
                .block();
    }

    // 종료를 위해 AI 서버를 호출하여 다음 시작 질문을 생성
    public AiNextOpeningResponse callFastApiForNextOpening(AiNextOpeningRequest request) {
        log.info("FastAPI 다음 회차 시작 질문 생성 요청 시작");

        return webClient.post()
                .uri("/api/v1/ai/interview/next-opening") // [cite: 19]
                .bodyValue(request)
                .retrieve()
                // 4xx 에러 처리 (문서에 명시된 422 누락 에러 등 방어)
                .onStatus(status -> status.is4xxClientError(), clientResponse -> {
                    log.error("FastAPI 클라이언트 에러 발생 (4xx): {}, 조합된 텍스트: {}",
                            clientResponse.statusCode(), request.getInterviewContext());
                    return Mono.error(new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR));
                })
                // 5xx 에러 처리
                .onStatus(status -> status.is5xxServerError(), clientResponse -> {
                    log.error("FastAPI 서버 에러 발생 (5xx): {}", clientResponse.statusCode());
                    return Mono.error(new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR));
                })
                .bodyToMono(AiNextOpeningResponse.class)
                .block();
    }

    // 종료를 위해 AI 서버를 호출하여 제목 요약
    public Mono<AiTitleResponse> callFastApiForInterviewTitleV2(AiTitleRequest request) {
        log.info("제목 생성 요청 시작");

        return webClient.post()
                .uri("/api/v1/ai/interview/title")
                .bodyValue(request)
                .retrieve()
                // 4xx 에러 처리 (문서에 명시된 422 에러 등)
                .onStatus(status -> status.is4xxClientError(), clientResponse -> {
                    log.error("FastAPI 클라이언트 에러 발생 (4xx): {}", clientResponse.statusCode());
                    // ErrorStatus.FAST_API_ERROR 등 본인 프로젝트에 맞는 에러 코드로 수정하세요
                    return Mono.error(new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR));
                })
                // 5xx 에러 처리 (FastAPI 서버가 죽었거나 모델에서 에러 났을 때)
                .onStatus(status -> status.is5xxServerError(), clientResponse -> {
                    log.error("FastAPI 서버 에러 발생 (5xx): {}", clientResponse.statusCode());
                    return Mono.error(new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR));
                })
                // JSON 응답을 AiTitleResponse 객체로 역직렬화
                .bodyToMono(AiTitleResponse.class);
    }

    // 종료를 위해 AI 서버를 호출하여 다음 시작 질문을 생성
    public Mono<AiNextOpeningResponse> callFastApiForNextOpeningV2(AiNextOpeningRequest request) {
        log.info("FastAPI 다음 회차 시작 질문 생성 요청 시작");

        return webClient.post()
                .uri("/api/v1/ai/interview/next-opening") // [cite: 19]
                .bodyValue(request)
                .retrieve()
                // 4xx 에러 처리 (문서에 명시된 422 누락 에러 등 방어)
                .onStatus(status -> status.is4xxClientError(), clientResponse -> {
                    log.error("FastAPI 클라이언트 에러 발생 (4xx): {}, 조합된 텍스트: {}",
                            clientResponse.statusCode(), request.getInterviewContext());
                    return Mono.error(new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR));
                })
                // 5xx 에러 처리
                .onStatus(status -> status.is5xxServerError(), clientResponse -> {
                    log.error("FastAPI 서버 에러 발생 (5xx): {}", clientResponse.statusCode());
                    return Mono.error(new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR));
                })
                .bodyToMono(AiNextOpeningResponse.class);
    }

    // 키워드 생성 메서드
    public Mono<KeywordResponse> callFastApiKeyword(KeywordRequest request) {

        log.info("FastAPI 키워드 추출 요청 시작");

        return webClient.post()
                .uri("api/v1/ai/interview/keyword")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), clientResponse -> {
                    log.error("FastAPI RAG Reindex 클라이언트 에러 발생 (4xx): {}",
                            clientResponse.statusCode());
                    // 4xx 에러는 재시도를 방지하기 위해 IllegalArgumentException으로 던집니다.
                    return Mono.error(new IllegalArgumentException("4xx Client Error"));
                })
                .onStatus(status -> status.is5xxServerError(), clientResponse -> {
                    log.error("FastAPI RAG Reindex 서버 에러 발생 (5xx): {}",
                            clientResponse.statusCode());
                    return Mono.error(new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR));
                })
                .bodyToMono(KeywordResponse.class);
    }

    // RAG 호출 관련 메서드 -----------------------------------------------------------------------------------------------------

    /**
     * 사용자의 전체 매거진 인터뷰 이력을 기준으로 RAG 재구성 (비동기)
     */
    public Mono<ReindexResponse> callFastApiForRagReindex(ReindexRequest request) {
        log.info("AI RAG 전체 재구성 요청 시작 - userId: {}", request.getUserId());

        return webClient.post()
                .uri("/api/v1/ai/interview/rag/reindex")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), clientResponse -> {
                    log.error("FastAPI RAG Reindex 클라이언트 에러 발생 (4xx): {}",
                            clientResponse.statusCode());
                    // 4xx 에러는 재시도를 방지하기 위해 IllegalArgumentException으로 던집니다.
                    return Mono.error(new IllegalArgumentException("4xx Client Error"));
                })
                .onStatus(status -> status.is5xxServerError(), clientResponse -> {
                    log.error("FastAPI RAG Reindex 서버 에러 발생 (5xx): {}",
                            clientResponse.statusCode());
                    return Mono.error(new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR));
                })
                .bodyToMono(ReindexResponse.class)
                .retryWhen(getRagRetrySpec());
    }

    /**
     * 새로운 매거진 인터뷰 문답 생성 시 RAG 추가 적재 (비동기)
     */
    public Mono<UpsertResponse> callFastApiForRagUpsert(UpsertRequest request) {
        log.info("AI RAG 추가 적재 요청 시작 - interviewId: {}", request.getInterviewId());

        return webClient.post()
                .uri("/api/v1/ai/interview/rag/upsert")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), clientResponse -> {
                    log.error("FastAPI RAG Upsert 클라이언트 에러 발생 (4xx): {}",
                            clientResponse.statusCode());
                    return Mono.error(new IllegalArgumentException("4xx Client Error"));
                })
                .onStatus(status -> status.is5xxServerError(), clientResponse -> {
                    log.error("FastAPI RAG Upsert 서버 에러 발생 (5xx): {}", clientResponse.statusCode());
                    return Mono.error(new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR));
                })
                .bodyToMono(UpsertResponse.class)
                .retryWhen(getRagRetrySpec());
    }

    /**
     * 특정 매거진 인터뷰를 사용자 RAG에서 제거 (비동기)
     */
    public Mono<DeleteResponse> callFastApiForRagDelete(DeleteRequest request) {
        log.info("AI RAG 삭제 요청 시작 - interviewId: {}", request.getInterviewId());

        return webClient.method(HttpMethod.DELETE)
                .uri("/api/v1/ai/interview/rag/delete")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), clientResponse -> {
                    log.error("FastAPI RAG Delete 클라이언트 에러 발생 (4xx): {}",
                            clientResponse.statusCode());
                    return Mono.error(new IllegalArgumentException("4xx Client Error"));
                })
                .onStatus(status -> status.is5xxServerError(), clientResponse -> {
                    log.error("FastAPI RAG Delete 서버 에러 발생 (5xx): {}", clientResponse.statusCode());
                    return Mono.error(new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR));
                })
                .bodyToMono(DeleteResponse.class)
                .retryWhen(getRagRetrySpec());
    }

    /**
     * RAG 관련 API 통신 실패 시 재시도(Retry) 정책을 정의하는 헬퍼 메서드 - 정책: 2초 간격으로 최대 3회 재시도 (Fixed Delay) - 필터링:
     * IllegalArgumentException(4xx 에러) 발생 시 재시도 생략
     */
    private Retry getRagRetrySpec() {
        return Retry.fixedDelay(3, Duration.ofSeconds(2))
                .filter(throwable -> {
                    if (throwable instanceof IllegalArgumentException) {
                        log.warn("🚨 RAG 연동 4xx 에러. 멱등성이 없으므로 재시도를 생략합니다.");
                        return false;
                    }
                    log.warn("⚠️ RAG 연동 5xx 서버 에러 또는 네트워크 단절. 재시도를 시작합니다.");
                    return true;
                });
    }
}
