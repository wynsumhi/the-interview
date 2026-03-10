package com.theinterview.domain.member.service;

import com.theinterview.domain.member.Member;
import com.theinterview.domain.member.dto.MemberRequest.MemberJoinRequest;
import com.theinterview.domain.member.dto.MemberResponse.MemberInfoDTO;
import com.theinterview.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MemberInfoDTO createMember(
            MemberJoinRequest request
    ) {
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
}
