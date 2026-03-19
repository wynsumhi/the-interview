package com.theinterview.domain.interview.test;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
public class TestSttService {

    private final WebClient webClient;

    public String callSttApi(byte[] audioBytes) {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("audio_file", new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return "chunk.webm";
            }
        }).contentType(MediaType.parseMediaType("audio/webm"));

        return webClient.post()
                .uri("/api/v1/ai/stt")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                // ⭐️ 수정됨: JsonNode 대신 DTO 클래스 사용
                .bodyToMono(SttResponseDto.class)
                .map(SttResponseDto::getSttText) // DTO에서 텍스트만 추출
                .block();
    }
}