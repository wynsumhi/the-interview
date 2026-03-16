package com.theinterview.domain.interview.service;

import com.theinterview.domain.interview.Repository.InterviewAnswerRepository;
import com.theinterview.domain.interview.Repository.InterviewQuestionRepository;
import com.theinterview.domain.interview.Repository.InterviewRepository;
import com.theinterview.domain.interview.dto.FastApiResponse.AiPipelineResponse;
import com.theinterview.domain.interview.dto.InterviewRequest.AudioMessageRequest;
import com.theinterview.domain.interview.dto.InterviewResponse.FollowUpQuestionDTO;
import com.theinterview.domain.interview.dto.InterviewResponse.WebSocketConnectResponse;
import com.theinterview.domain.interview.entity.Interview;
import com.theinterview.domain.interview.entity.InterviewAnswer;
import com.theinterview.domain.interview.entity.InterviewQuestion;
import com.theinterview.domain.interview.enums.InterviewStatus;
import com.theinterview.domain.member.Member;
import com.theinterview.domain.member.repository.MemberRepository;
import com.theinterview.global.response.exception.GeneralException;
import com.theinterview.global.response.status.ErrorStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final InterviewQuestionRepository interviewQuestionRepository;
    private final InterviewAnswerRepository interviewAnswerRepository;

    @Value("${file.audio.base-path}")
    private String answerAudioPath;

    @Transactional
    public FollowUpQuestionDTO oneQnAInterviewFlow(AudioMessageRequest request)
            throws IOException {

        // 필요 도메인 엔티티 조회
        Interview interview = interviewRepository.findById(request.getInterviewId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.INTERVIEW_NOT_FOUND));
        Member member = interview.getMember();

        // 현재 클라이언트가 구독중인 방 번호(interviewId)
        Integer interviewId = request.getInterviewId();

        // 클라이언트에서 보낸 Base64 데이터를 순수 바이트 배열로 변환
        byte[] audioBytes = Base64.getDecoder().decode(request.getAudioData());
        log.info("수신된 파일 크기: {} bytes", audioBytes.length);

        // 파일 저장 유틸 메서드 호출
        String savedFilePath = saveAnswerFile(audioBytes, member.getId(), interviewId);

        // ai 서버 호출
        AiPipelineResponse aiPipelineResponse = callFastApiForNextQuestion(audioBytes);

        // 현재 사용자가 답변한 질문
        InterviewQuestion question = interviewQuestionRepository.findById(request.getQuestionId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.QUESTION_NOT_FOUND));

        // 사용자의 답변 저장
        InterviewAnswer answer =
                interviewAnswerRepository.save(InterviewAnswer.builder()
                        .interviewQuestion(question)
                        .answerAudioUrl(savedFilePath)
                        .answerText("아직 텍스트를 반환하는 로직이 구현되지 않아 임시 텍스트를 반환합니다.")
                        .build());

        // 생성된 질문 저장
        InterviewQuestion generatedQuestion = interviewQuestionRepository.save(
                InterviewQuestion.builder()
                        .questionText(aiPipelineResponse.getQuestionText())
                        .sequence(question.getSequence() + 1)
                        .interview(interview)
                        .build());

        // 생성된 텍스트 및 오디오 파일 반환
        return FollowUpQuestionDTO.builder()
                .interviewId(interviewId)
                .sttResult("현재 텍스트 제공 x")
                .followUpQuestion(aiPipelineResponse.getQuestionText())
                .ttsAudioBase64(aiPipelineResponse.getTtsAudioBase64()) // 임시 원본 데이터 반환
                .questionId(generatedQuestion.getId())
                .build();
    }

    /**
     * 음성 바이트 배열을 지정된 경로에 저장하는 유틸 메서드
     *
     */
    private String saveAnswerFile(byte[] audioBytes, Integer memberId, Integer interviewId)
            throws IOException {

        String fileName = UUID.randomUUID().toString() + ".webm";

        Path directoryPath = Paths.get(answerAudioPath, String.valueOf(memberId),
                String.valueOf(interviewId));

        // 디렉토리가 없다면 생성
        if (!Files.exists(directoryPath)) {
            Files.createDirectories(directoryPath);
        }

        // 최종 파일 경로 조립 후 byte 배열 쓰기
        Path filePath = directoryPath.resolve(fileName);
        Files.write(filePath, audioBytes);

        log.info("음성 파일 저장 완료: {}", filePath.toAbsolutePath());

        // DB에 저장할 경로 문자열 반환
        return filePath.toAbsolutePath().toString();
    }


    @Transactional
    public WebSocketConnectResponse issueWebSocketId(String email) {

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));

        // 새로운 인터뷰 세션 생성 및 DB에 저장
        Interview interview = interviewRepository.save(Interview.builder()
                .member(member)
                .shareRange(true)
                .title("아직 질문 관련 로직/데이터가 구축되지 않아, 임시 텍스트를 반환합니다.")
                .status(InterviewStatus.BEFORE_START)
                .build());
        log.info("인터뷰 세션 생성 성공 interviewId: {}", interview.getId());

        // 생성된 질문 DB에 삽입
        InterviewQuestion question = interviewQuestionRepository.save(InterviewQuestion.builder()
                .interview(interview)
                .sequence(1)
                .questionText(interview.getTitle())
                .build());

        return WebSocketConnectResponse.builder()
                .interviewId(interview.getId())
                .questionId(question.getId())
                .interviewTitle(question.getQuestionText())
                .build();
    }


    // fast api를 호출하여 꼬리질문 관련 파이프라인을 거치는 헬퍼 메서드
    public AiPipelineResponse callFastApiForNextQuestion(byte[] audioBytes) {

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

        return aiPipelineResponse;
    }
}
