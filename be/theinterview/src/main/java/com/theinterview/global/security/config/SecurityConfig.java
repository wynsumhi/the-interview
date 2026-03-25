package com.theinterview.global.security.config;

import com.theinterview.global.config.redis.RedisService;
import com.theinterview.global.security.config.jwt.JwtFilter;
import com.theinterview.global.security.config.jwt.JwtProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // JWT 발급과 검증에 사용하는 제공자입니다.
    private final JwtProvider jwtProvider;
    // 블랙리스트와 리프레시 토큰 확인에 사용하는 Redis 서비스입니다.
    private final RedisService redisService;

    // 비밀번호는 BCrypt 방식으로 암호화합니다.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // JWT 기반 API 서버이므로 CSRF를 비활성화합니다.
                .csrf(AbstractHttpConfigurer::disable)
                // 세션 로그인 폼을 사용하지 않으므로 비활성화합니다.
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // 서버는 상태를 저장하지 않는 Stateless 방식으로 동작합니다.
                .sessionManagement(
                        session -> session.sessionCreationPolicy((SessionCreationPolicy.STATELESS)))
                // 공개 API와 인증이 필요한 API를 분리합니다.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/v1/interview/sound/play/**",
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
                        // 공유 보드 GET 조회는 userUuid만 알면 접근 가능하므로 공개합니다.
                        .requestMatchers(HttpMethod.GET, "/v1/share-board/member/**").permitAll()
                        .anyRequest().authenticated()
                ).cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // JWT 필터를 인증 필터 앞단에 배치합니다.
                .addFilterBefore(new JwtFilter(jwtProvider, redisService),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of(
                // 운영 프론트 주소입니다.
                "https://j14a307.p.ssafy.io",
                // 로컬 백엔드 테스트 주소입니다.
                "http://localhost:8080",
                // 로컬 프론트 개발 주소입니다.
                "http://localhost:3000",
                "http://70.12.247.129:3000",
                "http://127.0.0.1:5500",
                "http://localhost:5500"
        ));
        // 모든 HTTP 메서드를 허용합니다.
        configuration.addAllowedMethod("*");

        // 모든 요청 헤더를 허용합니다.
        configuration.setAllowedHeaders(List.of("*"));

        // 인증 헤더와 쿠키 전달이 가능하도록 credentials를 허용합니다.
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 전체 API 경로에 동일한 CORS 정책을 적용합니다.
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
