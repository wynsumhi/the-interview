package com.theinterview.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${fastapi.base-path}")
    private String fastApiUrl;

    @Value("${fastapi.service-token:}")
    private String fastApiServiceToken;

    @Bean
    public WebClient fastApiWebClient() {

        // FastAPI 응답에는 base64 오디오 문자열처럼 상대적으로 큰 payload가 포함될 수 있다.
        // 기본 설정으로는 디코딩 과정에서 메모리 버퍼 한도가 부족할 수 있으므로,
        // FastAPI 전용 WebClient에서는 in-memory 버퍼 크기를 16MB로 명시적으로 확장한다.
        final int MAX_IN_MEMORY_SIZE = 16 * 1024 * 1024;

        // 위에서 정한 메모리 버퍼 정책을 WebClient codec 설정에 반영한다.
        // 이 설정은 인터뷰 파이프라인, STT, RAG 호출 전체에 공통으로 적용된다.
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .build();

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(fastApiUrl)
                .exchangeStrategies(exchangeStrategies);

        // AI FastAPI는 보호 대상 엔드포인트에 대해 내부 서비스 토큰을 요구한다.
        // 각 호출 메서드에서 Authorization 헤더를 개별적으로 붙이면
        // 1. 누락 가능성이 커지고
        // 2. 인증 로직이 여러 파일에 중복되며
        // 3. 토큰 변경 시 수정 범위가 불필요하게 넓어진다.
        //
        // 그래서 FastAPI 전용 WebClient bean 생성 시점에 기본 헤더를 공통 적용한다.
        // 이렇게 하면 이 WebClient를 사용하는 모든 FastAPI 호출이 동일한 인증 정책을 공유한다.
        if (StringUtils.hasText(fastApiServiceToken)) {
            builder.defaultHeader("Authorization", "Bearer " + fastApiServiceToken);
        }

        return builder.build();
    }
}
