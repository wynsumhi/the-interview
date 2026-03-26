package com.theinterview.global.security.config;

import com.theinterview.global.config.redis.RedisService;
import com.theinterview.global.security.config.internal.InternalApiTokenFilter;
import com.theinterview.global.security.config.jwt.JwtFilter;
import com.theinterview.global.security.config.jwt.JwtProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.ForwardedHeaderFilter;

@Configuration
@EnableWebSecurity // 스프링 시큐리티 필터 체인을 위해 활성화
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final RedisService redisService;

    /*
     * AI 서버가 백엔드 내부 callback API를 호출할 때 사용할 전용 토큰이다.
     * 일반 사용자 JWT와 완전히 분리된 내부 서버 간 인증 값을 사용해야 하므로
     * SecurityConfig에서 별도 프로퍼티로 주입받아 InternalApiTokenFilter에 전달한다.
     */
    @Value("${internal.api.ai-callback-token:}")
    private String internalAiCallbackToken;

    // 비밀번호 암호화 메서드 등록
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // csrf 비활성화
                .formLogin(AbstractHttpConfigurer::disable) // HTTP 기본 form 로그인 비활성화
                .httpBasic(AbstractHttpConfigurer::disable)
                // 사용자 정보를 메모리에 저장하지 않고 무상태로 관리함
                .sessionManagement(
                        session -> session.sessionCreationPolicy((SessionCreationPolicy.STATELESS)))
                // 요청에 대한 권한 설정
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/v1/interview/sound/play/**",
                                "/v1/interview/internal/**",
                                "/api/v1/interview/internal/**",
                                "/v1/interview/share/**",
                                "/v1/auth/signup",
                                "/v1/auth/exists/**",
                                "/v1/api-docs/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api/v1/auth/login",
                                "/v1/auth/login",
                                "/ws-stt/**",
                                "/ws-stt",
                                "/ws-connect/**",
                                "/error"
                        ).permitAll()
                        // 공유 보드 GET 조회는 userUuid만 알면 접근 가능하도록 공개
                        .requestMatchers(HttpMethod.GET, "/v1/share-board/member/**").permitAll()
                        .anyRequest().authenticated()
                ).cors(cors -> cors.configurationSource(corsConfigurationSource()))
                /*
                 * 내부 callback 경로는 사용자 JWT 검증보다 먼저 별도 토큰 검증을 통과해야 한다.
                 * permitAll로 공개 경로에 포함하더라도 실제 접근은 InternalApiTokenFilter가 막기 때문에
                 * 기존 사용자 인증 체계를 건드리지 않고 AI 서버 전용 진입점만 따로 보호할 수 있다.
                 */
                .addFilterBefore(new InternalApiTokenFilter(internalAiCallbackToken),
                        JwtFilter.class)
                // JwtFilter를 시큐리티 체인에 등록함
                .addFilterBefore(new JwtFilter(jwtProvider, redisService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of(
                "https://j14a307.p.ssafy.io",      // 배포 환경
                "http://localhost:8080",           // 로컬 테스트용
                "http://localhost:3000",           // 리액트 기본 포트
                "http://70.12.247.129:3000",
                "http://127.0.0.1:5500",
                "http://localhost:5500"
        ));
        // 모든 HTTP 메서드 허용
        configuration.addAllowedMethod("*");

        // 요청 헤더 허용(우선 모두 허용)
        configuration.setAllowedHeaders(List.of("*"));

        // 쿠키나 인증정보를 포함한 요청 허용
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // 모든 API 경로에 이 규칙 적용
        return source;
    }

    @Bean
    public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
        FilterRegistrationBean<ForwardedHeaderFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new ForwardedHeaderFilter());
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
