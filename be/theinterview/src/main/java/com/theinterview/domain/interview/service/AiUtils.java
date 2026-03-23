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
import com.theinterview.domain.interview.dto.FastApiResponse.AiNextOpeningResponse;
import com.theinterview.domain.interview.dto.FastApiResponse.AiPipelineResponse;
import com.theinterview.domain.interview.dto.FastApiResponse.AiStartTtsResponse;
import com.theinterview.domain.interview.dto.FastApiResponse.AiTitleResponse;
import com.theinterview.domain.interview.dto.InterviewRequest.AudioChunkRequest;
import com.theinterview.domain.interview.dto.InterviewResponse.SttChunkResponse;
import com.theinterview.global.response.exception.GeneralException;
import com.theinterview.global.response.status.ErrorStatus;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

// TODO:
// 현재 클래스는 block() 기반 동기 호출과 Mono 기반 비동기 호출이 함께 섞여 있다.
// 지금은 기존 인터뷰 흐름과의 호환성을 우선 유지하기 위해 그대로 두었지만,
// 추후에는 호출 방식을 일관되게 정리해 트랜잭션 경계와 예외 전파를 더 명확하게 만들 필요가 있다.
@Slf4j
@Component
@RequiredArgsConstructor
public class AiUtils {

    private final WebClient webClient;

    /**
     * 클라이언트가 전송한 오디오 청크를 FastAPI STT 엔드포인트로 전달한다.
     *
     * 이 메서드는 웹소켓 기반 청크 업로드 흐름에서 사용되며,
     * 브라우저에서 넘어온 바이너리 오디오 데이터를 multipart/form-data 형식으로 감싸
     * FastAPI의 `/api/v1/ai/stt` 엔드포인트에 그대로 전달한다.
     *
     * FastAPI 인증 계층이 추가된 이후에도 별도의 헤더 조립 코드를 여기서 작성하지 않는 이유는,
     * Authorization 헤더가 WebClient bean 레벨에서 공통으로 주입되도록 설계했기 때문이다.
     * 따라서 이 메서드는 요청 body 조립과 응답 처리에만 집중한다.
     */
    public Mono<SttChunkResponse> callFastApiForSttChunk(AudioChunkRequest request) {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

        ByteArrayResource audioResource = new ByteArrayResource(request.getAudioData()) {
            @Override
            public String getFilename() {
                return "answer.webm";
            }
        };
        bodyBuilder.part("audio_file", audioResource)
                .contentType(MediaType.parseMediaType("audio/webm"));

        log.info("Starting FastAPI STT chunk request.");

        return webClient.post()
                .uri("api/v1/ai/stt")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                .onStatus(status -> status.is4xxClientError(),
                        clientResponse -> handleFastApi4xx("STT chunk", clientResponse))
                .onStatus(status -> status.is5xxServerError(),
                        clientResponse -> handleFastApi5xx("STT chunk", clientResponse))
                .bodyToMono(SttChunkResponse.class);
    }

    /**
     * 외부 STT 결과를 포함해 FastAPI 인터뷰 파이프라인을 호출한다.
     *
     * 이 메서드는 청크 단위 STT를 모두 모아 최종 텍스트가 이미 확보된 상태에서 사용된다.
     * 즉, FastAPI가 STT를 다시 수행하는 대신
     * 감정 분석, 후속 질문 생성, TTS 생성 등 STT 이후 단계만 이어서 실행하도록 만든다.
     */
    public Mono<AiPipelineResponse> callFastApiForNextQuestionV2(
            byte[] audioBytes,
            int sequence,
            String nickname,
            String previousQuestion,
            Boolean isFirstFinished,
            String sttText) {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

        ByteArrayResource audioResource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return "answer.webm";
            }
        };
        bodyBuilder.part("audio_file", audioResource)
                .contentType(MediaType.parseMediaType("audio/webm"));

        bodyBuilder.part("is_first_finished", isFirstFinished);
        bodyBuilder.part("sequence", sequence);
        bodyBuilder.part("nickname", nickname);

        if (sequence >= 2 && previousQuestion != null) {
            bodyBuilder.part("previous_question", previousQuestion);
        }

        bodyBuilder.part("stt_text", sttText);

        log.info("Starting FastAPI request: {}", "/api/v1/ai/interview/pipeline/after-stt");

        return webClient.post()
                .uri("/api/v1/ai/interview/pipeline/after-stt")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                .onStatus(status -> status.is4xxClientError(),
                        clientResponse -> handleFastApi4xx("pipeline after-stt", clientResponse))
                .onStatus(status -> status.is5xxServerError(),
                        clientResponse -> handleFastApi5xx("pipeline after-stt", clientResponse))
                .bodyToMono(AiPipelineResponse.class);
    }

    /**
     * FastAPI 인터뷰 파이프라인을 동기 방식으로 호출한다.
     */
    public AiPipelineResponse callFastApiForNextQuestion(
            byte[] audioBytes,
            int sequence,
            String nickname,
            String previousQuestion,
            Boolean isFirstFinished) {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

        ByteArrayResource audioResource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return "answer.webm";
            }
        };
        bodyBuilder.part("audio_file", audioResource)
                .contentType(MediaType.parseMediaType("audio/webm"));

        bodyBuilder.part("is_first_finished", isFirstFinished);
        bodyBuilder.part("sequence", sequence);
        bodyBuilder.part("nickname", nickname);

        if (sequence >= 2 && previousQuestion != null) {
            bodyBuilder.part("previous_question", previousQuestion);
        }

        log.info("Starting FastAPI request: {}", "/api/v1/ai/interview/pipeline");

        return webClient.post()
                .uri("/api/v1/ai/interview/pipeline")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                .onStatus(status -> status.is4xxClientError(),
                        clientResponse -> handleFastApi4xx("pipeline", clientResponse))
                .onStatus(status -> status.is5xxServerError(),
                        clientResponse -> handleFastApi5xx("pipeline", clientResponse))
                .bodyToMono(AiPipelineResponse.class)
                .block();
    }

    /**
     * 인터뷰 시작 질문과 TTS 생성을 FastAPI에 비동기 요청한다.
     *
     * `InterviewFacade`에서 체인 방식으로 바로 이어 쓰고 있으므로,
     * 이 메서드는 `Mono<AiStartTtsResponse>` 를 반환한다.
     */
    public Mono<AiStartTtsResponse> callFastApiForStartTts(AiStartTtsRequest request) {
        log.info("Starting FastAPI request: {}", "/api/v1/ai/interview/start-tts");

        return webClient.post()
                .uri("/api/v1/ai/interview/start-tts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(),
                        clientResponse -> handleFastApi4xx("start-tts", clientResponse))
                .onStatus(status -> status.is5xxServerError(),
                        clientResponse -> handleFastApi5xx("start-tts", clientResponse))
                .bodyToMono(AiStartTtsResponse.class)
                .doOnNext(aiStartTtsResponse -> log.info(
                        "FastAPI start-tts completed. text={}",
                        aiStartTtsResponse.getQuestionText()))
                .doOnError(error -> log.error("FastAPI start-tts failed: {}", error.getMessage()));
    }

    /**
     * 인터뷰 종료 시 사용할 제목 생성을 FastAPI에 동기 요청한다.
     */
    public AiTitleResponse callFastApiForInterviewTitle(AiTitleRequest request) {
        log.info("Starting FastAPI request: {}", "/api/v1/ai/interview/title");

        return webClient.post()
                .uri("/api/v1/ai/interview/title")
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(),
                        clientResponse -> handleFastApi4xx("title", clientResponse))
                .onStatus(status -> status.is5xxServerError(),
                        clientResponse -> handleFastApi5xx("title", clientResponse))
                .bodyToMono(AiTitleResponse.class)
                .block();
    }

    /**
     * 인터뷰 종료 시 사용할 제목 생성을 FastAPI에 비동기 요청한다.
     */
    public Mono<AiTitleResponse> callFastApiForInterviewTitleV2(AiTitleRequest request) {
        log.info("Starting FastAPI request: {}", "/api/v1/ai/interview/title");

        return webClient.post()
                .uri("/api/v1/ai/interview/title")
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(),
                        clientResponse -> handleFastApi4xx("titleV2", clientResponse))
                .onStatus(status -> status.is5xxServerError(),
                        clientResponse -> handleFastApi5xx("titleV2", clientResponse))
                .bodyToMono(AiTitleResponse.class);
    }

    /**
     * 다음 회차 인터뷰 시작 질문 생성을 FastAPI에 동기 요청한다.
     */
    public AiNextOpeningResponse callFastApiForNextOpening(AiNextOpeningRequest request) {
        log.info("Starting FastAPI request: {}", "/api/v1/ai/interview/next-opening");

        return webClient.post()
                .uri("/api/v1/ai/interview/next-opening")
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(),
                        clientResponse -> handleFastApi4xx(
                                "next-opening", clientResponse, request.getInterviewContext()))
                .onStatus(status -> status.is5xxServerError(),
                        clientResponse -> handleFastApi5xx("next-opening", clientResponse))
                .bodyToMono(AiNextOpeningResponse.class)
                .block();
    }

    /**
     * 다음 회차 인터뷰 시작 질문 생성을 FastAPI에 비동기 요청한다.
     */
    public Mono<AiNextOpeningResponse> callFastApiForNextOpeningV2(AiNextOpeningRequest request) {
        log.info("Starting FastAPI request: {}", "/api/v1/ai/interview/next-opening");

        return webClient.post()
                .uri("/api/v1/ai/interview/next-opening")
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(),
                        clientResponse -> handleFastApi4xx(
                                "next-openingV2", clientResponse, request.getInterviewContext()))
                .onStatus(status -> status.is5xxServerError(),
                        clientResponse -> handleFastApi5xx("next-openingV2", clientResponse))
                .bodyToMono(AiNextOpeningResponse.class);
    }

    /**
     * 사용자의 전체 인터뷰 이력을 기준으로 RAG 인덱스를 재구성한다.
     */
    public Mono<ReindexResponse> callFastApiForRagReindex(ReindexRequest request) {
        log.info("Starting FastAPI RAG reindex. userId={}", request.getUserId());

        return webClient.post()
                .uri("/api/v1/ai/interview/rag/reindex")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(),
                        clientResponse -> handleRag4xx("RAG reindex", clientResponse))
                .onStatus(status -> status.is5xxServerError(),
                        clientResponse -> handleFastApi5xx("RAG reindex", clientResponse))
                .bodyToMono(ReindexResponse.class)
                .retryWhen(getRagRetrySpec());
    }

    /**
     * 인터뷰 데이터를 사용자의 RAG 인덱스에 추가 또는 갱신한다.
     */
    public Mono<UpsertResponse> callFastApiForRagUpsert(UpsertRequest request) {
        log.info("Starting FastAPI RAG upsert. interviewId={}", request.getInterviewId());

        return webClient.post()
                .uri("/api/v1/ai/interview/rag/upsert")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(),
                        clientResponse -> handleRag4xx("RAG upsert", clientResponse))
                .onStatus(status -> status.is5xxServerError(),
                        clientResponse -> handleFastApi5xx("RAG upsert", clientResponse))
                .bodyToMono(UpsertResponse.class)
                .retryWhen(getRagRetrySpec());
    }

    /**
     * 인터뷰 데이터를 사용자의 RAG 인덱스에서 삭제한다.
     */
    public Mono<DeleteResponse> callFastApiForRagDelete(DeleteRequest request) {
        log.info("Starting FastAPI RAG delete. interviewId={}", request.getInterviewId());

        return webClient.method(HttpMethod.DELETE)
                .uri("/api/v1/ai/interview/rag/delete")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(),
                        clientResponse -> handleRag4xx("RAG delete", clientResponse))
                .onStatus(status -> status.is5xxServerError(),
                        clientResponse -> handleFastApi5xx("RAG delete", clientResponse))
                .bodyToMono(DeleteResponse.class)
                .retryWhen(getRagRetrySpec());
    }

    /**
     * FastAPI의 4xx 응답을 공통 처리한다.
     *
     * - 401: 내부 서비스 토큰 누락 또는 불일치
     * - 403: 토큰은 맞지만 필요한 scope가 없음
     * - 그 외 4xx: payload 또는 validation 오류 가능성
     */
    private Mono<? extends Throwable> handleFastApi4xx(
            String operation,
            ClientResponse clientResponse) {
        HttpStatus status = HttpStatus.resolve(clientResponse.statusCode().value());

        if (status == HttpStatus.UNAUTHORIZED) {
            log.error("FastAPI internal service auth failed. operation={}, status={}",
                    operation, clientResponse.statusCode());
        } else if (status == HttpStatus.FORBIDDEN) {
            log.error("FastAPI scope check failed. operation={}, status={}",
                    operation, clientResponse.statusCode());
        } else {
            log.error("FastAPI client error. operation={}, status={}",
                    operation, clientResponse.statusCode());
        }

        return Mono.error(new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR));
    }

    /**
     * 요청 문맥을 함께 남겨야 하는 4xx 응답 처리 메서드다.
     */
    private Mono<? extends Throwable> handleFastApi4xx(
            String operation,
            ClientResponse clientResponse,
            String requestContext) {
        HttpStatus status = HttpStatus.resolve(clientResponse.statusCode().value());

        if (status == HttpStatus.UNAUTHORIZED) {
            log.error("FastAPI internal service auth failed. operation={}, status={}, context={}",
                    operation, clientResponse.statusCode(), requestContext);
        } else if (status == HttpStatus.FORBIDDEN) {
            log.error("FastAPI scope check failed. operation={}, status={}, context={}",
                    operation, clientResponse.statusCode(), requestContext);
        } else {
            log.error("FastAPI client error. operation={}, status={}, context={}",
                    operation, clientResponse.statusCode(), requestContext);
        }

        return Mono.error(new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR));
    }

    /**
     * RAG 계열 4xx 응답을 처리한다.
     * 4xx는 재시도 대상에서 제외하기 위해 IllegalArgumentException으로 변환한다.
     */
    private Mono<? extends Throwable> handleRag4xx(
            String operation,
            ClientResponse clientResponse) {
        HttpStatus status = HttpStatus.resolve(clientResponse.statusCode().value());

        if (status == HttpStatus.UNAUTHORIZED) {
            log.error("FastAPI internal service auth failed. operation={}, status={}",
                    operation, clientResponse.statusCode());
        } else if (status == HttpStatus.FORBIDDEN) {
            log.error("FastAPI scope check failed. operation={}, status={}",
                    operation, clientResponse.statusCode());
        } else {
            log.error("FastAPI RAG client error. operation={}, status={}",
                    operation, clientResponse.statusCode());
        }

        return Mono.error(new IllegalArgumentException("4xx Client Error"));
    }

    /**
     * FastAPI의 5xx 응답을 공통 처리한다.
     */
    private Mono<? extends Throwable> handleFastApi5xx(
            String operation,
            ClientResponse clientResponse) {
        log.error("FastAPI server error. operation={}, status={}",
                operation, clientResponse.statusCode());
        return Mono.error(new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR));
    }

    /**
     * RAG 호출 재시도 정책.
     * - 2초 간격
     * - 최대 3회
     * - 4xx는 재시도하지 않음
     */
    private Retry getRagRetrySpec() {
        return Retry.fixedDelay(3, Duration.ofSeconds(2))
                .filter(throwable -> {
                    if (throwable instanceof IllegalArgumentException) {
                        log.warn("Skipping RAG retry because the failure is a 4xx client error.");
                        return false;
                    }
                    log.warn("Retrying RAG call because of server/network failure.");
                    return true;
                });
    }
}
