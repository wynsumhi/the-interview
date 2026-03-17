package com.theinterview.domain.interview.service;

import com.theinterview.domain.interview.dto.FastApiRequest.AiNextOpeningRequest;
import com.theinterview.domain.interview.dto.FastApiRequest.AiStartTtsRequest;
import com.theinterview.domain.interview.dto.FastApiRequest.AiTitleRequest;
import com.theinterview.domain.interview.dto.FastApiResponse.AiNextOpeningResponse;
import com.theinterview.domain.interview.dto.FastApiResponse.AiPipelineResponse;
import com.theinterview.domain.interview.dto.FastApiResponse.AiStartTtsResponse;
import com.theinterview.domain.interview.dto.FastApiResponse.AiTitleResponse;
import com.theinterview.global.response.exception.GeneralException;
import com.theinterview.global.response.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiUtils {

    private final WebClient webClient;

    // fast api를 호출하여 꼬리질문 관련 파이프라인을 거치는 헬퍼 메서드
    public AiPipelineResponse callFastApiForNextQuestion(byte[] audioBytes) {

        // muiltpart/form-data 생성을 위한 빌더
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

        // 순수 byte 배열에 파일명(.webm) 메타데이터를 강제 주입
        // 익명 클래스 활용으로 getFilename 메서드를 오버라이딩하여 해당 byte 배열의 이름을 강제로 변경함
        ByteArrayResource audioResource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return "answer.webm";
            }
        };

        // 명세서에 맞춰 데이터 삽입
        bodyBuilder.part("audio_file", audioResource)
                .contentType(MediaType.parseMediaType("audio/webm"));

        log.info("AI 서버와 통신 시작 요청 URI: {}", "/api/v1/ai/interview/pipeline");
        // 비동기 webclient를 이용한 post 요청
        AiPipelineResponse aiPipelineResponse = webClient.post()
                .uri("/api/v1/ai/interview/pipeline")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                .bodyToMono(AiPipelineResponse.class)
                .block();  // 웹소켓 흐름을 동기화하기 위해 대기(필요시 완전 비동기화 리팩터링) MVP 이후

        log.info("AI 서버와 통신 완료 반환된 텍스트: {}", aiPipelineResponse.getQuestionText());

        return aiPipelineResponse;
    }

    // 인터뷰 첫 시작 질문 TTS 반환을 위해 AI 서버 호출 메서드
    public AiStartTtsResponse callFastApiForStartTts(AiStartTtsRequest request
    ) {

        log.info("AI 서버와 TTS 통신 시작 uri: {}", "/api/v1/ai/interview/start-tts");
        // AI 서버 호출
        AiStartTtsResponse response = webClient.post()
                .uri("/api/v1/ai/interview/start-tts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AiStartTtsResponse.class)
                .block();

        if (response != null) {
            log.info("AI 서버와 TTS 통신 완료. 반환된 텍스트: {}", response.getQuestionText());
        }

        return response;
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
                    log.error("FastAPI 클라이언트 에러 발생 (4xx): {}", clientResponse.statusCode());
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
}
