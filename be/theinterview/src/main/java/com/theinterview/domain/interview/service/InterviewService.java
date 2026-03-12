package com.theinterview.domain.interview.service;

import com.theinterview.domain.interview.Interview;
import com.theinterview.domain.interview.Repository.InterviewRepository;
import com.theinterview.domain.interview.dto.FastApiResponse.AiPipelineResponse;
import com.theinterview.domain.interview.dto.InterviewRequest.AudioMessageRequest;
import com.theinterview.domain.interview.dto.InterviewResponse.FollowUpQuestionDTO;
import com.theinterview.domain.interview.enums.InterviewStatus;
import com.theinterview.domain.member.Member;
import com.theinterview.domain.member.repository.MemberRepository;
import com.theinterview.global.response.exception.GeneralException;
import com.theinterview.global.response.status.ErrorStatus;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewService {

    private final MemberRepository memberRepository;
    private final InterviewRepository interviewRepository;
    private final WebClient webClient;


    @Transactional
    public FollowUpQuestionDTO oneQnAInterviewFlow(AudioMessageRequest request) throws IOException {

        // 클라이언트에서 보낸 Base64 데이터를 순수 바이트 배열로 변환
        byte[] audioBytes = Base64.getDecoder().decode(request.getAudioData());
        log.info("수신된 파일 크기: {} bytes", audioBytes.length);

        // muiltpart/form-data 생성을 위한 빌더
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

        // 순수 byte 배열에 파일명(.webm) 메타데이터를 강제 주입
        // 익명 클래스 활용으로 getFilename 메서드를 오버라이딩하여 해당 byte 배열의 이름을 강제로 변경함
        ByteArrayResource audioResource = new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return "answer.webm";
            }
        };

        // 명세서에 맞춰 데이터 삽입
        bodyBuilder.part("audio_file", audioResource)
                .contentType(MediaType.parseMediaType("audio/webm"));

        log.info("AI 서버와 통신 시작 요청 URI: {}", "/api/v1/interviews/pipeline");
        // 비동기 webclient를 이용한 post 요청
        AiPipelineResponse aiPipelineResponse = webClient.post()
                .uri("/api/v1/interviews/pipeline")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                .bodyToMono(AiPipelineResponse.class)
                .block();  // 웹소켓 흐름을 동기화하기 위해 대기(필요시 완전 비동기화 리팩터링) MVP 이후

        log.info("AI 서버와 통신 완료 반환된 텍스트: {}", aiPipelineResponse.getQuestionText());

        // MVP: 오디오 파일 프로젝트 최상단에 저장
        File dest = new File("test_audio_" + request.getRoomId() + ".webm");
        Files.write(dest.toPath(), audioBytes);
        log.info("음성 파일 저장 완료: {}", dest.getAbsoluteFile());
        String roomId = request.getRoomId();

        // 생성된 텍스트 및 오디오 파일 반환
        return FollowUpQuestionDTO.builder()
                .roomId(roomId)
                .sttResult("현재 텍스트 제공 x")
                .followUpQuestion(aiPipelineResponse.getQuestionText())
                .ttsAudioBase64(aiPipelineResponse.getTtsAudioBase64()) // 임시 원본 데이터 반환
                .build();
    }

    @Transactional
    public Integer issueWebSocketId(String email) {

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));

        // 새로운 인터뷰 세션 생성 및 DB에 저장
        Interview interview = interviewRepository.save(Interview.builder()
                .member(member)
                .shareRange(true)
                .title("임시 제목")
                .status(InterviewStatus.BEFORE_START)
                .build());
        log.info("인터뷰 세션 생성 성공 interviewId: {}", interview.getId());

        return interview.getId();

    }


}
