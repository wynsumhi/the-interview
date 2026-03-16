package com.theinterview.domain.interview.service;

import com.theinterview.domain.interview.dto.FastApiRequest.AiStartTtsRequest;
import com.theinterview.domain.interview.dto.FastApiResponse.AiPipelineResponse;
import com.theinterview.domain.interview.dto.FastApiResponse.AiStartTtsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

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

        log.info("AI 서버와 통신 시작 요청 URI: {}", "/api/v1/ai/interviews/pipeline");
        // 비동기 webclient를 이용한 post 요청
        AiPipelineResponse aiPipelineResponse = webClient.post()
                .uri("/api/v1/ai/interviews/pipeline")
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

        log.info("AI 서버와 TTS 통신 시작 uri: {}", "/api/v1/ai/interviews/start-tts");
        // AI 서버 호출
        AiStartTtsResponse response = webClient.post()
                .uri("/api/v1/ai/interviews/start-tts")
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

}
