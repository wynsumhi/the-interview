package com.theinterview.global.security.config.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker // 내장된 STOMP 브로커 가동
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompHandler stompHandler;

    // 클라이언트가 연결을 시도할 최초의 엔드포인트를 설정
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-connect") // ws://localhost:8080/ws-connect 로 연결 시도
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

    // 클라이언트로부터 서버로 들어오는 메시지 통로(Inbound Channel)를 설정
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 해당 통로를 지나는 모든 메시지는 stompHandler를 거쳐가도록 설정
        registration.interceptors(stompHandler);
    }

    // websocket 버퍼 제한 및 용량이 클 때를 대비해 전송 시간 설정
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry
                .setMessageSizeLimit(50 * 1024 * 1024)
                .setSendBufferSizeLimit(50 * 1024 * 1024)
                .setSendTimeLimit(10 * 1000);
    }
}
