package com.theinterview.domain.member.controller;

import com.theinterview.domain.member.dto.MemberRequest.LoginRequestDTO;
import com.theinterview.domain.member.dto.MemberRequest.MemberJoinRequest;
import com.theinterview.domain.member.dto.MemberResponse.MemberInfoDTO;
import com.theinterview.domain.member.dto.MemberResponse.TokenResponseDTO;
import com.theinterview.domain.member.service.MemberService;
import com.theinterview.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/auth")
public class MemberController {

    private final MemberService memberService;

    /**
     * 회원가입 엔드포인트와 연결된 컨트롤러 메서드
     *
     * @param request: 사용자가 입력한 이메일, 비밀번호, 닉네임 정보가 담겨옴
     * @return
     */
    @PostMapping("/signup")
    public ApiResponse<MemberInfoDTO> createMember(
            @Valid @RequestBody MemberJoinRequest request
    ) {
        // 서비스 레이어 호출
        MemberInfoDTO memberInfoDTO = memberService.createMember(request);
        return ApiResponse.onSuccess(memberInfoDTO);
    }

    @GetMapping("/exists/email")
    public ApiResponse<Boolean> checkEmail(
            @Valid @RequestParam("email") String email
    ) {
        // 서비스 레이어 호출
        boolean response = memberService.checkEmail(email);
        return ApiResponse.onSuccess(response);
    }

    @GetMapping("/exists/nickname")
    public ApiResponse<Boolean> checkNickname(
            @Valid @RequestParam("nickname") String nickname
    ) {
        // 서비스 레이어 호출
        boolean response = memberService.checkNickname(nickname);
        return ApiResponse.onSuccess(response);
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponseDTO> login(
            @Valid @RequestBody LoginRequestDTO request
    ) {
        // 서비스 레이어 호출
        TokenResponseDTO response = memberService.login(request);
        return ApiResponse.onSuccess(response);
    }
}
