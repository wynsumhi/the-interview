package com.theinterview.global.security.config.websocket;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;

@Slf4j
public class StompHandler implements ChannelInterceptor {

    @Override
    public @Nullable Message<?> preSend(Message<?> message, MessageChannel channel) {

        // 날것의 메시지를 STOMP 전용 기능을 사용할 수 있는 Accessor로 캐스팅
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        // 서버에 최초로 연결(CONNECT)을 시도하는 커맨드인지 확인
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.info("웹소켓 CONNECT 요청, 토큰 검사 필요");
        }
        
        // CONNECT 요청이 아니거나 무사히 검증을 통과했다면 요청을 정상적으로 흘려보냄
        return message;
    }
}
