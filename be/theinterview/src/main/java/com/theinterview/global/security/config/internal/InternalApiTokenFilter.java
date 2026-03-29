package com.theinterview.global.security.config.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class InternalApiTokenFilter extends OncePerRequestFilter {

    private static final String INTERNAL_API_PREFIX = "/v1/interview/internal/";
    private static final String INTERNAL_API_CONTEXT_PREFIX = "/api/v1/interview/internal/";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final String internalApiToken;

    public InternalApiTokenFilter(String internalApiToken) {
        this.internalApiToken = internalApiToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return !requestUri.startsWith(INTERNAL_API_PREFIX)
                && !requestUri.startsWith(INTERNAL_API_CONTEXT_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        /*
         * 이 필터는 AI 서버가 호출하는 내부 전용 API 경로만 대상으로 동작한다.
         * 일반 사용자 JWT 인증 흐름과 섞이지 않도록 URI prefix 기준으로 대상을 한정하고,
         * 내부 API 요청에는 별도의 Bearer 토큰이 반드시 포함되도록 강제한다.
         */
        if (!StringUtils.hasText(internalApiToken)) {
            /*
             * 운영 설정에 내부 토큰이 비어 있으면 내부 API를 열어 두는 것이 더 위험하다.
             * 따라서 "토큰 미설정 = 접근 거부"로 처리해서 배포 설정 누락을 빠르게 드러내도록 한다.
             */
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Internal API token is not configured.");
            return;
        }

        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(authorizationHeader)
                || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            /*
             * 내부 API는 항상 Bearer 헤더를 사용하도록 통일한다.
             * 이렇게 해야 AI 서버와 백엔드 사이의 호출 규약이 단순해지고,
             * 추후 프록시나 게이트웨이를 추가하더라도 인증 헤더 규칙을 재사용하기 쉽다.
             */
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Missing internal bearer token.");
            return;
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length());
        if (!internalApiToken.equals(token)) {
            /*
             * 내부 토큰이 일치하지 않으면 곧바로 차단한다.
             * 이 경로는 사용자 요청이 아니라 서버 간 통신 전용이므로,
             * 토큰 불일치 상황은 곧바로 비정상 요청으로 간주하는 편이 안전하다.
             */
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid internal bearer token.");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
