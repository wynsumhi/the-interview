package com.theinterview.domain.interview.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.theinterview.domain.interview.dto.InterviewRequest.BookmarkSetting;
import com.theinterview.domain.interview.dto.InterviewRequest.InterviewExitRequest;
import com.theinterview.domain.interview.dto.InterviewRequest.InterviewShareRange;
import com.theinterview.domain.interview.dto.InterviewRequest.InterviewStartRequest;
import com.theinterview.domain.interview.dto.InterviewRequest.TotalChunkRequest;
import com.theinterview.domain.interview.dto.InterviewResponse.InterviewExploreResponse;
import com.theinterview.domain.interview.dto.InterviewResponse.InterviewHistoryDTO;
import com.theinterview.domain.interview.dto.InterviewResponse.InterviewHistoryDetailDTO;
import com.theinterview.domain.interview.dto.InterviewResponse.InterviewPostItBoardResponse;
import com.theinterview.domain.interview.dto.InterviewResponse.InterviewShareLink;
import com.theinterview.domain.interview.dto.InterviewResponse.SharedInterviewResponseDTO;
import com.theinterview.domain.interview.dto.InterviewResponse.WebSocketConnectResponse;
import com.theinterview.domain.interview.facade.InterviewFacade;
import com.theinterview.domain.interview.service.AiUtils;
import com.theinterview.domain.interview.service.InterviewService;
import com.theinterview.domain.member.Member;
import com.theinterview.domain.member.repository.MemberRepository;
import com.theinterview.global.config.redis.RedisService;
import com.theinterview.global.response.ApiResponse;
import com.theinterview.global.response.exception.GeneralException;
import com.theinterview.global.response.status.ErrorStatus;
import java.io.IOException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/v1/interview")
public class InterviewController {

    private final InterviewService interviewService;
    private final MemberRepository memberRepository;
    private final RedisService redisService;
    private final InterviewFacade interviewFacade;
    private final AiUtils aiUtils;

    // 웹소켓 연결 및 구독할 인터뷰 방의 번호(interviewID) 및 첫 질문의 번호(questionId)를 발급
    @PostMapping
    public ApiResponse<WebSocketConnectResponse> readForConnectWebSocket(
            @RequestBody InterviewStartRequest request,
            @AuthenticationPrincipal User user
    ) throws JsonProcessingException {
        String email = user.getUsername();
        // 파서드 레이어 호출
        return ApiResponse.onSuccess(
                interviewFacade.issueWebSocketId(email)
                        .block()); // 여기에서 톰캣 스레드가 멈춤(마치 기존의 async 처럼 동작함)
    }


    @PostMapping("/exit")
    public ApiResponse<String> exitInterview(
            @RequestBody InterviewExitRequest request,
            @AuthenticationPrincipal User user
    ) {
        String email = user.getUsername();
        // 서비스 레이어 호출
        return ApiResponse.onSuccess(interviewService.exitInterview(request, email));
    }

    @PostMapping("/exitV2")
    public ApiResponse<Void> exitInterviewV2(
            @RequestBody TotalChunkRequest request,
            @AuthenticationPrincipal User user
    ) throws IOException {

        String email = user.getUsername();

        // 락 체크 및 예외 처리(이미 저장중인 인터뷰 있는지 확인)
        if (redisService.hasKey("interviewId:" + request.getInterviewId() + "lock")) {
            throw new GeneralException(ErrorStatus.QUESTION_NOT_SAVED);
        }

        String redisKey =
                "interviewId:" + request.getInterviewId() + "questionId:" + request.getQuestionId();
        // redis 저장 중 표시
        redisService.setValues("interviewId:" + request.getInterviewId() + "isExit",
                "true", Duration.ofSeconds(20));
        redisService.setValues("interviewId:" + request.getInterviewId() + "isSaving", "inProgress",
                Duration.ofSeconds(30));

        // 오디오 파일을 받지 못한 경우(사용자가 녹음이 시작되기 전에 종료한 경우)
        if (request.getAnswerAudio() == null || request.getAnswerAudio().length == 0) {

            // redis에 락 처리
            redisService.setValues("interviewId:" + request.getInterviewId() + "lock", "lock",
                    Duration.ofSeconds(20));
            log.info("요청에 오디오 파일 없음. 오디오 파일 없는 종료 로직 시작");
            interviewFacade.exitInterviewNoAudioFacade(request, email,
                    "interviewId:" + request.getInterviewId());
            redisService.deleteValues("interviewId:" + request.getInterviewId() + "lock");
            // Finished 처리 및 rag 호출
        }

        // 서버 디렉토리에 음성 파일 저장
        String savedPath = interviewService.saveAnswerFile(request.getAnswerAudio(),
                request.getInterviewId());

        // redis 저장 코드 호출
        interviewService.saveRequiredFlagInRedis(savedPath, redisKey, request.getTotalChunk());

        return ApiResponse.OK;
    }

    // 인터뷰 목록 조회
    @GetMapping
    public ApiResponse<InterviewHistoryDTO> getInterviewsHistory(
            @RequestParam(defaultValue = "0") Integer page,
            @AuthenticationPrincipal User user
    ) {
        Member member = memberRepository.findByEmail(user.getUsername())
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));

        InterviewHistoryDTO interviewHistoryDTO = interviewService.getInterviewHistoryList(
                member.getId(), page);
        return ApiResponse.onSuccess(interviewHistoryDTO);
    }

    // 인터뷰 상세조회
    @GetMapping("/{interviewId}")
    public ApiResponse<InterviewHistoryDetailDTO> getInterviewHistoryDetail(
            @PathVariable(name = "interviewId") Integer interviewId,
            @AuthenticationPrincipal User user
    ) {
        Member member = memberRepository.findByEmail(user.getUsername())
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));

        InterviewHistoryDetailDTO interviewHistoryDetailDTO = interviewService.getInterviewHistoryDetail(
                interviewId, member.getId());
        return ApiResponse.onSuccess(interviewHistoryDetailDTO);
    }

    // 인터뷰 삭제
    @DeleteMapping("/{interviewId}")
    public ApiResponse<Void> deleteInterview(
            @PathVariable(name = "interviewId") Integer interviewId,
            @AuthenticationPrincipal User user
    ) {

        // 인터뷰 삭제 파서드 호출
        interviewFacade.deleteInterviewFacade(user.getUsername(), interviewId);

        return ApiResponse.OK;
    }

    // 인터뷰 상세페이지 공개 범위 설정
    @PostMapping("/{interviewId}/share/set")
    public ApiResponse<Void> setInterviewShareRange(
            @PathVariable(name = "interviewId") Integer interviewId,
            @RequestBody InterviewShareRange request,
            @AuthenticationPrincipal User user
    ) {
        String email = user.getUsername();
        interviewService.setInterviewShareRange(email, interviewId, request);
        return ApiResponse.OK;
    }

    // 인터뷰 상세페이지 공유 링크 발급
    @GetMapping("/{interviewId}/share")
    public ApiResponse<InterviewShareLink> getInterviewShareLink(
            @PathVariable(name = "interviewId") Integer interviewId,
            @AuthenticationPrincipal User user
    ) {
        String email = user.getUsername();
        InterviewShareLink interviewShareLinkDTO = interviewService.getInterviewShareLink(email,
                interviewId);
        return ApiResponse.onSuccess(interviewShareLinkDTO);
    }

    // 공유된 인터뷰 조회
    @GetMapping("/share/{shareUuid}")
    public ApiResponse<SharedInterviewResponseDTO> getSharedInterviewDetail(
            @PathVariable(name = "shareUuid") String shareUuid
    ) {
        SharedInterviewResponseDTO sharedInterviewResponseDTO = interviewService.getSharedInterviewDetail(
                shareUuid);
        return ApiResponse.onSuccess(sharedInterviewResponseDTO);
    }

    // 사용자 공유 페이지 ( 포스트 잇 )
    @GetMapping("/share/user/{userUuid}")
    public ApiResponse<InterviewPostItBoardResponse> getSharedPostItBoard(
            @PathVariable(name = "userUuid") String userUuid,
            @RequestParam(defaultValue = "0") Integer page
    ) {
        InterviewPostItBoardResponse interviewPostItBoardResponseDTO = interviewService.getSharedPostItBoard(
                userUuid, page);
        return ApiResponse.onSuccess(interviewPostItBoardResponseDTO);
    }

    // 인터뷰 탐색하기 조회
    @GetMapping("/explore")
    public ApiResponse<InterviewExploreResponse> getInterviewExplore(
            @RequestParam(defaultValue = "0") Integer page
    ) {
        InterviewExploreResponse interviewExploreResponseDTO = interviewService.getInterviewExplore(
                page);
        return ApiResponse.onSuccess(interviewExploreResponseDTO);
    }

    // 인터뷰 북마크 추가/삭제
    @PostMapping("/{interviewId}/share/bookmark")
    public ApiResponse<Void> setBookmark(
            @PathVariable(name = "interviewId") Integer interviewId,
            @RequestBody BookmarkSetting request,
            @AuthenticationPrincipal User user
    ) {
        String email = user.getUsername();
        interviewService.setBookmark(email, interviewId, request);
        return ApiResponse.OK;
    }

    // 사용자 인터뷰 오디오 재생
    @GetMapping("/sound/play/{voiceUuid}")
    public ResponseEntity<Resource> serveVoice(
            @PathVariable String voiceUuid,
            @AuthenticationPrincipal User user
    ) {
        // 음원 듣기는 공개 범위를 True로 두는경우 회원이 아니여도 가능하기 때문에 로그인 정보를 조회하고 null이라면 Null로 전달
        String email = (user != null) ? user.getUsername() : null;
        Resource resource = interviewService.getVoiceResource(email, voiceUuid);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg")) // 브라우저에게 오디오임을 알림
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline") // 브라우저 내에서 즉시 재생
                .body(resource);
    }
}
