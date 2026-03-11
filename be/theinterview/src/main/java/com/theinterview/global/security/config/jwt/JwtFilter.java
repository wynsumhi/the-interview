package com.theinterview.global.security.config.jwt;

import com.theinterview.global.config.redis.RedisService;
import com.theinterview.global.response.exception.GeneralException;
import com.theinterview.global.response.status.ErrorStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final RedisService redisService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String jwt = resolveToken(request);

        // 토큰이 존재하고 JwtProvider를 통해 유효성 검증을 통과했다면
        if (StringUtils.hasText(jwt) && jwtProvider.validateToken(jwt)) {
            // 현재 검사하려는 토큰이 블랙리스트로 등록되어있는지 확인함
            if (redisService.hasKey("BLACKLIST:" + jwt)) {
                log.warn("블랙리스트에 등록된 토큰으로 접속");
                throw new GeneralException(ErrorStatus.FORBIDDEN);
            }

            // 토큰에서 유저 정보(Authentication 객체)를 꺼냄
            Authentication authentication = jwtProvider.getAuthentication(jwt);

            // 스프링 시큐리티의 SecurityContext에 해당 유저 정보를 저장함
            // 해당 요청이 끝날 때 까지 유저가 인증된 유저라는 것을 기억함
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.info("인증 정보 저장 성공(사용자: {}, uri: {})", authentication.getName(),
                    request.getRequestURI());
        }
        filterChain.doFilter(request, response);
    }

    // Http Header에서 토큰 정보를 반환하는 헬퍼 메서드
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        // 토큰이 존재하고, "Bearer "로 시작한다면, "Bearer " 뒷부분(진짜 토큰)만 잘라서 반환
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
