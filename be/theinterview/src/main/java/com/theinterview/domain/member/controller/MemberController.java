package com.theinterview.domain.member.controller;

import com.theinterview.domain.member.dto.MemberRequest.LoginRequestDTO;
import com.theinterview.domain.member.dto.MemberRequest.MemberJoinRequest;
import com.theinterview.domain.member.dto.MemberResponse.MemberInfoDTO;
import com.theinterview.domain.member.dto.MemberResponse.MemberInfoResponse;
import com.theinterview.domain.member.dto.MemberResponse.TokenResponseDTO;
import com.theinterview.domain.member.service.MemberService;
import com.theinterview.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.util.StringUtils;
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
            @Valid @RequestBody LoginRequestDTO request,
            HttpServletResponse servletResponse
    ) {
        // 서비스 레이어 호출
        TokenResponseDTO response = memberService.login(request);

        // refreshToken 쿠키에 반영
        ResponseCookie refreshCookie = createCookie("refreshToken", response.getRefreshToken(),
                response.getRefreshTokenValidityTime());

        servletResponse.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        return ApiResponse.onSuccess(response);
    }

    @PostMapping("/logout")
    public ApiResponse<String> logout(
            @AuthenticationPrincipal User user,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String email = user.getUsername();

        // 요청 헤더에서 토큰을 추출하여 서비스 레이어로 넘겨줌
        String bearerToken = request.getHeader("Authorization");
        String accessToken = null;
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            accessToken = bearerToken.substring(7);
        }

        // 서비스 레이어 호출
        memberService.logout(email, accessToken);

        // 쿠키 삭제 및 응답 헤더에 반영
        ResponseCookie refreshCookie = createCookie("refreshToken", "", 0);
        // 응답 객체의 쿠키에 적용
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        return ApiResponse.onSuccess(null);
    }

    @GetMapping("/me")
    public ApiResponse<MemberInfoResponse> getMemberInfo(
            @AuthenticationPrincipal User user
    ) {
        // 서비스 레이어 호출
        MemberInfoResponse loginTestDTO = memberService.getMemberInfo(user.getUsername());
        return ApiResponse.onSuccess(loginTestDTO);
    }

    // 쿠키 생성 헬퍼 메서드
    private ResponseCookie createCookie(String name, String value, long maxAge) {
        return ResponseCookie.from(name, value)
                .path("/")
                .httpOnly(true)
                .maxAge(maxAge)
                .build();
    }
}
