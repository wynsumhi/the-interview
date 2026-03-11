package com.theinterview.global.security.config.websocket;

import com.theinterview.global.response.exception.GeneralException;
import com.theinterview.global.response.status.ErrorStatus;
import com.theinterview.global.security.config.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompHandler implements ChannelInterceptor {

    private final JwtProvider jwtProvider;

    @Override
    public @Nullable Message<?> preSend(Message<?> message, MessageChannel channel) {

        // 날것의 메시지를 STOMP 전용 기능을 사용할 수 있는 Accessor로 캐스팅
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        // 서버에 최초로 연결(CONNECT)을 시도하는 커맨드인지 확인
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {

            // STOMP 헤더에서 "Authorization 키에 담긴 값을 꺼냄
            String authorizationHeader = accessor.getFirstNativeHeader("Authorization");

            // 값이 존재하고 Bearer로 시작한다면 access 토큰
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer")) {

                // Bearer 7글자를 잘라내고 순수한 JWT 문자열만 추출
                String token = authorizationHeader.substring(7);
                log.info("웹소켓 연결을 위한 토큰 확인 완료 token: {}", token);

                // 토큰의 위조 여부, 만료 여부 확인
                if (jwtProvider.validateToken(token)) {

                    // 토큰이 진짜라면 토큰에서 유저 정보를 뽑아냄
                    Authentication authentication = jwtProvider.getAuthentication(token);
                    // 연결하려는 웹소켓 세션의 주인을 명시함
                    accessor.setUser(authentication);
                    log.info("웹소켓 연결됨 사용자: {}", authentication.getName());
                } else {
                    // 토큰이 가짜거나 만료되었다면 예외 발생
                    log.info("웹소켓 연결 거부");
                    throw new GeneralException(ErrorStatus.FORBIDDEN);
                }
            } else {
                // 토큰이 아예 없거나 형식이 틀리다면 연결 거부
                log.info("웹소켓 연결 거부");
                throw new GeneralException(ErrorStatus.FORBIDDEN);
            }
        }
        // CONNECT 요청이 아니거나 무사히 검증을 통과했다면 요청을 정상적으로 흘려보냄
        return message;
    }
}
