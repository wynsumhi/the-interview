package com.theinterview.global.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity // 스프링 시큐리티 필터 체인을 위해 활성화
public class SecurityConfig {

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
                                "/v1/auth/exists/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}
