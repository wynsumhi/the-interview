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
}
