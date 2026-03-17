package com.theinterview.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${fastapi.base-path}")
    private String fastApiUrl;

    @Bean
    public WebClient fastApiWebClient() {

        // 메모리 버퍼 사이즈 변경(일단 16mb)
        final int MAX_IN_MEMORY_SIZE = 16 * 1024 * 1024;

        // WebClient의 코덱 제한을 변경
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .build();

        return WebClient.builder()
                .baseUrl(fastApiUrl)
                .exchangeStrategies(exchangeStrategies)
                .build();
    }
}
