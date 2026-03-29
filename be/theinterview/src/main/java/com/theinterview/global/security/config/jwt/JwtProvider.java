package com.theinterview.global.security.config.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Getter
public class JwtProvider {

    private final SecretKey key;
    private final long accessTokenValidityTime;
    private final long refreshTokenValidityTime;

    public JwtProvider(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.refresh-token-validity-in-seconds}") long refreshTokenValidityTime,
            @Value("${jwt.access-token-validity-in-seconds}") long accessTokenValidityTime
    ) {
        // 키 암호화
        byte[] keyByes = Decoders.BASE64.decode(secretKey);
        this.key = Keys.hmacShaKeyFor(keyByes);

        // 밀리초 단위 변경
        this.accessTokenValidityTime = accessTokenValidityTime * 1000;
        this.refreshTokenValidityTime = refreshTokenValidityTime * 1000;
    }

    // Access Token 생성 메서드
    public String createAccessToken(Authentication authentication) {
        long now = new Date().getTime();
        Date validity = new Date(now + this.accessTokenValidityTime);

        return Jwts.builder()
                .subject(authentication.getName())
                .expiration(validity)
                .signWith(key)
                .compact();
    }

    // Refresh Token 생성 메서드
    public String createRefreshToken(Authentication authentication) {
        long now = new Date().getTime();
        Date validity = new Date(now + this.refreshTokenValidityTime);

        return Jwts.builder()
                .subject(authentication.getName())
                .expiration(validity)
                .signWith(key)
                .compact();
    }

    // 토큰 정보를 열어보고 유저 정보를 꺼내는 메서드
    public Authentication getAuthentication(String token) {
        // 서명을 확인하고 페이로드를 파싱함
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        // 사용자 권한 명시
        Collection<? extends GrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_USER"));

        // 토큰에서 꺼낸 이메일과 기본 권한으로 1회용 인증 정보를 조립해 반환함
        User principal = new User(claims.getSubject(), "", authorities);
        return new UsernamePasswordAuthenticationToken(principal, token, authorities);
    }

    // 토큰 유효성 검사 메서드
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.info("잘못된 JWT 서명");
        } catch (ExpiredJwtException e) {
            log.info("만료된 JWT 토큰");
        } catch (UnsupportedJwtException e) {
            log.info("지원되지 않는 JWT 토큰");
        } catch (IllegalArgumentException e) {
            log.info("잘못된 JWT 토큰");
        }

        return false;
    }

    public long getRemainingTime(String accessToken) {
        try {
            // 서명 확인 및 페이로드 파싱
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(accessToken)
                    .getPayload();

            // 2. 만료 시간과 현재 시간
            Date expiration = claims.getExpiration();
            long now = new Date().getTime();

            // 3. 남은 시간(밀리초)을 계산해서 반환
            return expiration.getTime() - now;

        } catch (ExpiredJwtException e) {
            // 이미 만료된 토큰이라면 굳이 블랙리스트에 넣을 필요가 없으므로 0을 반환
            log.info("이미 만료된 토큰입니다. 블랙리스트 처리를 생략합니다.");
            return 0;
        } catch (Exception e) {
            // 다른 형식이 깨진 토큰이어도 0을 반환하여 무시 처리
            return 0;
        }
    }
}
