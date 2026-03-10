package com.theinterview.global.security.config.jwt;

import com.theinterview.domain.member.Member;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails {

    // member 도메인 객체
    private final Member member;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 관리자 페이지 없음 -> 모든 회원에게 일관적으로 USER 롤 부여
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    // 시큐리티에서 사용할 비밀번호
    @Override
    public String getPassword() {
        return member.getPassword();
    }

    // 시큐리티에서 사용할 아이디(이메일)
    @Override
    public String getUsername() {
        return member.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    // 계정 만료, 잠금, 비밀번호 만료 활성화 여부(일단 모두 true로 열어둠)
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
