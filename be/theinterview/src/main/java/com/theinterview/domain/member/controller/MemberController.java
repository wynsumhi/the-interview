package com.theinterview.domain.member.controller;

import com.theinterview.domain.member.dto.MemberRequest.MemberJoinRequest;
import com.theinterview.domain.member.dto.MemberResponse.MemberInfoDTO;
import com.theinterview.domain.member.service.MemberService;
import com.theinterview.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/auth")
public class MemberController {

    private final MemberService memberService;

    @PostMapping("/signup")
    public ApiResponse<MemberInfoDTO> createMember(
            @Valid @RequestBody MemberJoinRequest request
    ) {
        // 서비스 레이어 호출
        MemberInfoDTO memberInfoDTO = memberService.createMember(request);
        return ApiResponse.onSuccess(memberInfoDTO);
    }
}
