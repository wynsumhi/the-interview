package com.theinterview.global.security.config.jwt;

import com.theinterview.domain.member.Member;
import com.theinterview.domain.member.repository.MemberRepository;
import com.theinterview.global.response.exception.GeneralException;
import com.theinterview.global.response.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    // 시큐리티가 로그인 요청을 받으면 해당 메서드를 호출하여 DB에서 유저 정보를 찾음
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));
        // 찾은 Member 객체를 시큐리티 규격의 CustomUserDetails로 반환
        return new CustomUserDetails(member);
    }
}
