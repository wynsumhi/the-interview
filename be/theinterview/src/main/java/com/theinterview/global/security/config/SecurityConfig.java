package com.theinterview.global.security.config;

import com.theinterview.global.config.redis.RedisService;
import com.theinterview.global.security.config.jwt.JwtFilter;
import com.theinterview.global.security.config.jwt.JwtProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
@EnableWebSecurity // 스프링 시큐리티 필터 체인을 위해 활성화
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final RedisService redisService;

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
                        // 로그인 하지 않은 사용자의 요청을 허용 할 엔드포인트는 여기에 작성하세요.
                        .requestMatchers(
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
                        .anyRequest().authenticated()
                ).cors(cors -> cors.configurationSource(corsConfigurationSource()))
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
}
