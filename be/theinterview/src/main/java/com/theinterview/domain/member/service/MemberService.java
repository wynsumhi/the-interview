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
import com.theinterview.domain.shareboard.entity.ShareBoard;
import com.theinterview.domain.shareboard.repository.ShareBoardPostitLayoutRepository;
import com.theinterview.domain.shareboard.repository.ShareBoardQuestionRepository;
import com.theinterview.domain.shareboard.repository.ShareBoardRepository;
import com.theinterview.global.config.redis.RedisService;
import com.theinterview.global.response.exception.GeneralException;
import com.theinterview.global.response.status.ErrorStatus;
import com.theinterview.global.security.config.jwt.JwtProvider;
import com.theinterview.global.util.DomainFindUtils;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
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

    // 회원 기본 정보를 조회하고 저장하는 저장소입니다.
    private final MemberRepository memberRepository;
    // 회원이 가진 인터뷰 목록을 조회하는 저장소입니다.
    private final InterviewRepository interviewRepository;
    // 인터뷰 삭제 시 하위 데이터 정리를 재사용하기 위한 서비스입니다.
    private final InterviewService interviewService;
    // 회원가입 시 공유 보드를 생성하고 탈퇴 시 삭제하기 위한 저장소입니다.
    private final ShareBoardRepository shareBoardRepository;
    // 회원 탈퇴 시 공유 보드 질문을 함께 삭제하기 위한 저장소입니다.
    private final ShareBoardQuestionRepository shareBoardQuestionRepository;
    // 회원 탈퇴 시 포스트잇 레이아웃을 함께 삭제하기 위한 저장소입니다.
    private final ShareBoardPostitLayoutRepository shareBoardPostitLayoutRepository;
    // 로그인 응답에 필요한 userUuid 조회를 위해 사용하는 유틸입니다.
    private final DomainFindUtils domainFindUtils;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final JwtProvider jwtProvider;
    private final RedisService redisService;

    @Value("${jwt.refresh-token-validity-in-seconds}")
    private long refreshTokenValidityInSeconds;

    @Transactional
    public MemberInfoDTO createMember(MemberJoinRequest request) {
        // 이메일과 닉네임 중 하나라도 중복이면 회원가입을 허용하지 않습니다.
        if (!checkEmail(request.getEmail()) || !checkNickname(request.getNickname())) {
            throw new GeneralException(ErrorStatus.MEMBER_ALREADY_EXISTS);
        }

        // 비밀번호는 반드시 암호화해서 저장합니다.
        Member member = Member.builder()
                .email(request.getEmail())
                .nickname(request.getNickname())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        // 회원 저장 직후 회원과 1:1인 공유 보드를 자동 생성합니다.
        Member saveMember = memberRepository.save(member);
        shareBoardRepository.save(ShareBoard.builder()
                .owner(saveMember)
                .build());

        return new MemberInfoDTO(saveMember);
    }

    @Transactional(readOnly = true)
    public TokenResponseDTO login(LoginRequestDTO request) {
        // 로그인 응답에 포함할 userUuid를 조회합니다.
        Member member = domainFindUtils.getMemberByEmail(request.getEmail());

        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(request.getEmail(),
                        request.getPassword());

        Authentication authenticate = authenticationManagerBuilder.getObject()
                .authenticate(authenticationToken);

        String accessToken = jwtProvider.createAccessToken(authenticationToken);
        String refreshToken = jwtProvider.createRefreshToken(authenticationToken);

        redisService.setValues(
                authenticate.getName(),
                refreshToken,
                Duration.ofSeconds(refreshTokenValidityInSeconds)
        );

        return new TokenResponseDTO(refreshToken, accessToken, member.getUserUuid(),
                jwtProvider.getRefreshTokenValidityTime(),
                jwtProvider.getAccessTokenValidityTime()
        );
    }

    @Transactional
    public void logout(String email, String accessToken) {
        // Redis에 저장된 리프레시 토큰을 제거해 재발급을 막습니다.
        if (email != null && redisService.hasKey(email)) {
            redisService.deleteValues(email);
        }

        // 아직 유효한 액세스 토큰은 블랙리스트에 등록합니다.
        if (accessToken != null) {
            long remainingTime = jwtProvider.getRemainingTime(accessToken);

            if (remainingTime > 0) {
                redisService.setValues(
                        "BLACKLIST:" + accessToken,
                        "logout",
                        Duration.ofMillis(remainingTime)

                );
            }
        }
    }

    @Transactional(readOnly = true)
    public MemberInfoDTO getMemberInfo(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));

        return new MemberInfoDTO(member);
    }

    @Transactional
    public MemberInfoDTO updateMemberInfo(String email, MemberInfoUpdateRequest request) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));

        if (!Objects.equals(request.getNickname(), member.getNickname())
                && !checkNickname(request.getNickname())) {
            throw new GeneralException(ErrorStatus.MEMBER_ALREADY_EXISTS);
        }

        member.changeMemberInfo(request, passwordEncoder);
        return new MemberInfoDTO(member);
    }

    public void deleteMemberInfo(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));

        // 회원이 보유한 인터뷰는 기존 삭제 로직으로 정리합니다.
        List<Interview> interviews = interviewRepository.findAllByMemberId(member.getId());

        if (!interviews.isEmpty()) {
            interviews.forEach(interview ->
                    interviewService.deleteInterview(interview.getId(), member.getId()));
        }

        // 공유 보드 하위 데이터는 FK 순서에 맞춰 레이아웃 -> 질문 -> 보드 순으로 삭제합니다.
        shareBoardRepository.findByOwnerId(member.getId()).ifPresent(shareBoard -> {
            shareBoardPostitLayoutRepository.deleteAllByShareBoardId(shareBoard.getId());
            shareBoardQuestionRepository.deleteAllByShareBoardId(shareBoard.getId());
            shareBoardRepository.deleteByOwnerId(member.getId());
        });

        // 마지막으로 회원 본체를 삭제합니다.
        memberRepository.deleteById(member.getId());
    }

    public boolean checkEmail(String email) {
        return !memberRepository.existsByEmail(email);
    }

    public boolean checkNickname(String nickname) {
        return !memberRepository.existsByNickname(nickname);
    }
}
