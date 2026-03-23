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
     * 따라서 이 메서드는 "요청 body 조립과 응답 처리"라는 본연의 역할에만 집중한다.
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
     *
     * multipart body를 유지하는 이유는 텍스트뿐 아니라 원본 오디오 파일도 함께 넘겨
     * 후속 파이프라인이 동일한 입력 컨텍스트를 활용할 수 있게 하기 위해서다.
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
     *
     * 기존 서비스 흐름에서는 다음 질문 생성 결과가 즉시 필요하므로,
     * 이 메서드는 Mono를 그대로 넘기지 않고 내부에서 block() 하여 결과를 반환한다.
     *
     * 요청 본문에는 오디오 파일과 인터뷰 진행 상태가 함께 포함되며,
     * FastAPI는 이를 바탕으로 STT, 상태 분석, 후속 질문 생성, TTS 생성을 한 번에 수행한다.
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
     * 인터뷰 시작 질문과 TTS 생성을 FastAPI에 요청한다.
     *
     * 첫 인터뷰 사용자와 기존 인터뷰 이력이 있는 사용자는
     * 시작 질문을 만드는 방식이 다를 수 있으므로,
     * request DTO에는 그 분기에 필요한 상태 값이 포함된다.
     *
     * 응답에는 질문 텍스트뿐 아니라 base64 인코딩된 TTS 오디오가 들어오며,
     * 백엔드는 이를 그대로 상위 계층 응답으로 전달한다.
     */
    public AiStartTtsResponse callFastApiForStartTts(AiStartTtsRequest request) {
        log.info("Starting FastAPI request: {}", "/api/v1/ai/interview/start-tts");

        AiStartTtsResponse response = webClient.post()
                .uri("/api/v1/ai/interview/start-tts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(status -> status.is4xxClientError(),
                        clientResponse -> handleFastApi4xx("start-tts", clientResponse))
                .onStatus(status -> status.is5xxServerError(),
                        clientResponse -> handleFastApi5xx("start-tts", clientResponse))
                .bodyToMono(AiStartTtsResponse.class)
                .block();

        if (response != null) {
            log.info("FastAPI start-tts completed. text={}", response.getQuestionText());
        }

        return response;
    }

    /**
     * 인터뷰 종료 시 사용할 제목 생성을 FastAPI에 요청한다.
     *
     * 백엔드는 인터뷰 전체 문맥을 문자열로 조합해 전달하고,
     * FastAPI는 이를 바탕으로 매거진 제목 형태의 결과를 생성한다.
     *
     * 이 호출도 보호 대상 엔드포인트이므로,
     * 내부 서비스 토큰이 WebClient 레벨에서 자동 부착된다는 점이 중요하다.
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
     * 다음 회차 인터뷰 시작 질문 생성을 FastAPI에 요청한다.
     *
     * 사용자가 이번 인터뷰를 끝낸 뒤, 다음 인터뷰에서 사용할 opening question을 만들기 위해 호출한다.
     * request 안에는 인터뷰 전체 문맥과 닉네임이 들어가며,
     * FastAPI는 이를 기반으로 다음 회차의 첫 질문 후보를 생성해 반환한다.
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
     * 사용자의 전체 인터뷰 이력을 기준으로 RAG 인덱스를 재구성한다.
     *
     * 개별 문답 추가가 아니라, 사용자의 전체 인터뷰 기록을 기준으로
     * RAG 저장소를 처음부터 다시 맞추고 싶을 때 사용하는 호출이다.
     * 배치성 작업 또는 정합성 회복 작업에 가깝기 때문에 비동기 Mono 흐름으로 유지한다.
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
     *
     * 새 인터뷰가 저장되었을 때 해당 문답만 RAG에 반영하기 위한 호출이다.
     * 전체 재색인보다 비용이 적고, 일반적인 인터뷰 종료 후 후처리에 사용된다.
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
     *
     * 사용자가 인터뷰 이력을 삭제했을 때,
     * DB 삭제와 별개로 벡터 저장소에서도 동일한 인터뷰 데이터를 제거하기 위해 사용한다.
     * 삭제 계열 호출이므로 재시도 정책과 4xx 분기 처리가 특히 중요하다.
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
     * 이번 인증 계층 추가 이후 4xx는 단순한 요청 오류만 의미하지 않는다.
     * 특히 아래 두 경우는 운영 중 원인 파악이 매우 중요하다.
     *
     * - 401: 내부 서비스 토큰 누락 또는 불일치
     * - 403: 토큰은 맞지만 필요한 scope가 없음
     *
     * 그래서 일반 4xx와 인증/인가 실패를 로그에서 명확히 구분해 남긴다.
     * 현재 외부로는 기존과 동일하게 내부 서버 오류로 감싸서 던지지만,
     * 로그만으로도 설정 문제인지 payload 문제인지 빠르게 구분할 수 있게 한다.
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
     *
     * `next-opening`처럼 입력 문맥 자체가 디버깅에 중요한 호출에서는
     * 어떤 요청 문맥으로 실패했는지 로그에 같이 남기는 편이 운영에 유리하다.
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
     *
     * RAG upsert/reindex/delete는 재시도 정책이 붙어 있기 때문에,
     * 4xx를 일반 서버 오류처럼 다루면 의미 없는 재시도가 발생할 수 있다.
     * 따라서 기존 정책을 유지해 4xx는 `IllegalArgumentException`으로 바꿔
     * retry filter에서 재시도 대상에서 제외되도록 만든다.
     *
     * 다만 이번 변경 이후 401/403은 설정 문제일 가능성이 크므로,
     * 로그는 인증 실패/권한 부족으로 분리해서 남긴다.
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
     *
     * 이 경우는 대체로 FastAPI 내부 처리 실패, 모델 서버 문제,
     * 또는 외부 연동 장애에 해당하므로 인증 오류와는 분리해서 기록한다.
     */
    private Mono<? extends Throwable> handleFastApi5xx(
            String operation,
            ClientResponse clientResponse) {
        log.error("FastAPI server error. operation={}, status={}",
                operation, clientResponse.statusCode());
        return Mono.error(new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR));
    }

    /**
     * RAG 호출 재시도 정책을 정의한다.
     *
     * - 재시도 간격: 2초
     * - 최대 재시도 횟수: 3회
     * - 재시도 제외 대상: 4xx 기반 클라이언트 오류
     *
     * RAG는 외부 저장소(Qdrant, 임베딩 서버 등) 의존성이 있어
     * 일시적 네트워크 실패나 서버 오류가 날 수 있으므로 제한적 재시도를 둔다.
     * 반면 4xx는 요청 자체 또는 인증 설정 문제일 가능성이 높아 재시도해도 이득이 없다.
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
