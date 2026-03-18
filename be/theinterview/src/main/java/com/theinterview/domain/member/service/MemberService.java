package com.theinterview.domain.member.service;

import com.theinterview.domain.interview.Repository.InterviewRepository;
import com.theinterview.domain.interview.entity.Interview;
import com.theinterview.domain.interview.service.InterviewService;
import com.theinterview.domain.member.Member;
import com.theinterview.domain.member.dto.MemberRequest.LoginRequestDTO;
import com.theinterview.domain.member.dto.MemberRequest.MemberInfoUpdateRequest;
import com.theinterview.domain.member.dto.MemberRequest.MemberJoinRequest;
import com.theinterview.domain.member.dto.MemberResponse.MemberInfoDTO;
import com.theinterview.domain.member.dto.MemberResponse.TokenResponseDTO;
import com.theinterview.domain.member.repository.MemberRepository;
import com.theinterview.global.config.redis.RedisService;
import com.theinterview.global.response.exception.GeneralException;
import com.theinterview.global.response.status.ErrorStatus;
import com.theinterview.global.security.config.jwt.JwtProvider;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final InterviewRepository interviewRepository;
    private final InterviewService interviewService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final JwtProvider jwtProvider;
    private final RedisService redisService;

    // yml -> refresh token 만료 시간
    @Value("${jwt.refresh-token-validity-in-seconds}")
    private long refreshTokenValidityInSeconds;

    // 회원가입 메서드
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

        // redis에 refresh 토큰 저장
        redisService.setValues(
                authenticate.getName(),
                refreshToken,
                Duration.ofSeconds(refreshTokenValidityInSeconds)
        );

        return new TokenResponseDTO(refreshToken, accessToken,
                jwtProvider.getRefreshTokenValidityTime(),
                jwtProvider.getAccessTokenValidityTime()
        );
    }

    // 로그아웃 메서드
    @Transactional
    public void logout(String email, String accessToken) {

        // Redis에 해당 이메일로 저장된 데이터가 있는지 확인하고 데이터 삭제
        if (email != null && redisService.hasKey(email)) {
            redisService.deleteValues(email);
            log.info("로그아웃 처리 완료 - 사용자: {}", email);
        }

        // accessToken 블랙리스트 처리
        if (accessToken != null) {
            long remainingTime = jwtProvider.getRemainingTime(accessToken);

            if (remainingTime > 0) {
                // 남은 시간만큼만 레디스에 저장함 (Key: BLACKLIST: 토큰, Value: "logout")
                redisService.setValues(
                        "BLACKLIST:" + accessToken,
                        "logout",
                        Duration.ofMillis(remainingTime)

                );
                log.info("Access Token 블랙리스트 추가 완료");
            }
        }
    }

    // 회원 정보 조회 메서드
    @Transactional(readOnly = true)
    public MemberInfoDTO getMemberInfo(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));

        return new MemberInfoDTO(member);
    }

    // 회원 정보 수정 메서드
    @Transactional
    public MemberInfoDTO updateMemberInfo(String email, MemberInfoUpdateRequest request) {

        // 닉네임 중복 검사
        if (!checkNickname(request.getNickname())) {
            throw new GeneralException(ErrorStatus.MEMBER_ALREADY_EXISTS);
        }

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));

        member.changeMemberInfo(request);
        return new MemberInfoDTO(member);

    }

    // 회원탈퇴
    public void deleteMemberInfo(String email) {

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));

        // FK 제약조건으로 인하여 자식부터 삭제하기 위해서 모든 인터뷰 내역을 가져옴
        List<Interview> interviews = interviewRepository.findAllByMemberId(member.getId());

        log.info("[MemberService - deleteMemberInfo] memberID : {} / Load Interviews Size : {}",
                member.getId(), interviews.size());

        // 만약 인터뷰 이력이 있다면 모든 인터뷰 데이터 삭제
        if (!interviews.isEmpty()) {
            interviews.forEach(interview ->
                    // interviewService의 사용자 인터뷰 삭제 로직 동작
                    interviewService.deleteInterview(interview.getId(), member.getId()));
        }

        memberRepository.deleteById(member.getId());
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
}
