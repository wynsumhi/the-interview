package com.theinterview.global.security.config.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker // 내장된 STOMP 브로커 가동
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // 클라이언트가 연결을 시도할 최초의 엔드포인트를 설정
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-stt") // ws://localhost:8080/sw-stt 로 연결 시도
                .setAllowedOriginPatterns("*") // cors 설정, 일단 모두 허용(todo: security cors 설정 반영 예정)
                .withSockJS();
    }

    // 메시지 브로커 라우팅 규칙 설정
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {

        // 수신: 프론트엔드가 데이터를 받을(구독) 경로의 접두사
        registry.enableSimpleBroker("/topic");

        // 송신: 프론트엔드가 서버로 데이터를 보낼(발행) 경로의 접두사
        // 프론트가 데이터를 보내면 서버의 @MessageMapping 컨트롤러가 낚아챔
        registry.setApplicationDestinationPrefixes("/app");
    }
}
