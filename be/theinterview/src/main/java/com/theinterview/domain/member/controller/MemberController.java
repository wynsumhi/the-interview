package com.theinterview.domain.member.controller;

import com.theinterview.domain.member.dto.MemberRequest.MemberJoinRequest;
import com.theinterview.domain.member.dto.MemberResponse.MemberInfoDTO;
import com.theinterview.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/v1/auth")
public class MemberController {

    @PostMapping("/signup")
    public ApiResponse<MemberInfoDTO> createMember(
            @Valid @RequestBody MemberJoinRequest request
    ) {
        // 서비스 레이어 호출

        return null;
    }
}
