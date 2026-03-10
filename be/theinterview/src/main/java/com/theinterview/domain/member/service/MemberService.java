package com.theinterview.domain.member.service;

import com.theinterview.domain.member.Member;
import com.theinterview.domain.member.dto.MemberRequest.LoginRequestDTO;
import com.theinterview.domain.member.dto.MemberRequest.MemberJoinRequest;
import com.theinterview.domain.member.dto.MemberResponse.MemberInfoDTO;
import com.theinterview.domain.member.dto.MemberResponse.TokenResponseDTO;
import com.theinterview.domain.member.repository.MemberRepository;
import com.theinterview.global.response.exception.GeneralException;
import com.theinterview.global.response.status.ErrorStatus;
import com.theinterview.global.security.config.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final JwtProvider jwtProvider;

    @Transactional
    public MemberInfoDTO createMember(
            MemberJoinRequest request
    ) {
        if (!checkEmail(request.getEmail())) {
            throw new GeneralException(ErrorStatus.MEMBER_ALREADY_EXISTS);
        }

        if (!checkNickname(request.getNickname())) {
            throw new GeneralException(ErrorStatus.MEMBER_ALREADY_EXISTS);
        }

        // 비밀번호 암호화 후 엔티티 생성 및 DB 저장
        Member member = Member.builder()
                .email(request.getEmail())
                .nickname(request.getNickname())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        // 사용자 정보 DB에 저장
        Member saveMember = memberRepository.save(member);
        log.info("회원가입 완료 memberId: {}, email: {}, nickname: {}", saveMember.getId(),
                saveMember.getEmail(), saveMember.getNickname());

        return new MemberInfoDTO(saveMember);
    }

    // 이메일 중복 체크 헬퍼 메서드
    public boolean checkEmail(String email) {
        // 이메일 중복 체크
        return !memberRepository.existsByEmail(email);
    }

    // 닉네임 중복 체크 헬퍼 메서드
    public boolean checkNickname(String nickname) {
        return !memberRepository.existsByNickname(nickname);
    }

    // 로그인 및 토큰 발급 로직
    @Transactional(readOnly = true)
    public TokenResponseDTO login(LoginRequestDTO request) {

        // 클라이언트에서 보맨 이메일과 비밀번호를 기반으로 임시 토큰 생성
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                request.getEmail(), request.getPassword());

        // loadUserByUsername 메서드가 실행되면서 Db에서 사용자를 찾고 비밀번호를 비교함(CustomUserDetailsService)
        Authentication authenticate = authenticationManagerBuilder.getObject()
                .authenticate(authenticationToken);

        // 인증을 통과했다면 토큰을 생성함
        String accessToken = jwtProvider.createAccessToken(authenticationToken);
        String refreshToken = jwtProvider.createRefreshToken(authenticationToken);

        // 여기서 redis 저장 로직 예정

        return new TokenResponseDTO(refreshToken, accessToken);
    }
}
