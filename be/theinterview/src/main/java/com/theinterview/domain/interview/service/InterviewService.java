package com.theinterview.domain.interview.service;

import com.theinterview.domain.interview.Repository.InterviewAnswerRepository;
import com.theinterview.domain.interview.Repository.InterviewQuestionRepository;
import com.theinterview.domain.interview.Repository.InterviewRepository;
import com.theinterview.domain.interview.dto.FastApiRequest.AiStartTtsRequest;
import com.theinterview.domain.interview.dto.FastApiResponse.AiPipelineResponse;
import com.theinterview.domain.interview.dto.FastApiResponse.AiStartTtsResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final AiUtils aiUtils;

    @Value("${file.audio.base-path}")
    private String answerAudioPath;

    // 인터뷰 진행 중 꼬리 질문 생성 메서드
    @Transactional
    public FollowUpQuestionDTO oneQnAInterviewFlow(AudioMessageRequest request)
            throws IOException {

        // 필요 도메인 엔티티 조회
        Interview interview = interviewRepository.findById(request.getInterviewId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.INTERVIEW_NOT_FOUND));
        Member member = interview.getMember();

        // 현재 사용자가 답변한 질문
        InterviewQuestion question = interviewQuestionRepository.findById(request.getQuestionId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.QUESTION_NOT_FOUND));

        // 사용자 답변 저장 및 꼬리질문 생성 로직 시작이라고 표시
        question.interviewQuestionInProgress();
        log.info("사용자 답변 저장 및 꼬리질문 생성 로직 시작 status: {}", question.getStatus());

        // 현재 클라이언트가 구독중인 방 번호(interviewId)
        Integer interviewId = request.getInterviewId();

        // 클라이언트에서 보낸 Base64 데이터를 순수 바이트 배열로 변환
        byte[] audioBytes = Base64.getDecoder().decode(request.getAudioData());
        log.info("수신된 파일 크기: {} bytes", audioBytes.length);

        // 파일 저장 유틸 메서드 호출
        String savedFilePath = saveAnswerFile(audioBytes, member.getId(), interviewId);

        // ai 서버 호출
        AiPipelineResponse aiPipelineResponse = aiUtils.callFastApiForNextQuestion(audioBytes);

        // 사용자의 답변 저장
        InterviewAnswer answer =
                interviewAnswerRepository.save(InterviewAnswer.builder()
                        .interviewQuestion(question)
                        .answerAudioUrl(savedFilePath)
                        .answerText("아직 텍스트를 반환하는 로직이 구현되지 않아 임시 텍스트를 반환합니다.")
                        .build());

        // 사용자의 답변이 저장되었다면 해당 interviewQuestion 객체의 status를 답변 완료(종료로 바꿈)
        question.interviewQuestionFinished();

        log.info("사용자 답변 저장 및 꼬리질문 생성 로직 완료 status: {}", question.getStatus());

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


    // 웹소켓 연결 전, 클라이언트가 구독 할 인터뷰 pk를 발급하고, 첫 질문 텍스트를 TTS로 변환하여 전달함
    @Transactional
    public WebSocketConnectResponse issueWebSocketId(String email) {

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));

        // 시작 질문 및 TTS 변환 요청
        AiStartTtsResponse aiStartTtsResponse = aiUtils.callFastApiForStartTts(AiStartTtsRequest
                .builder()
                .dbQuestion(null)
                .nickname(member.getNickname())
                .isFirstFinished(member.getIsFirstFinished())
                .build());

        // 새로운 인터뷰 세션 생성 및 DB에 저장(todo: 첫 질문 생성 로직 완료 시 첫 사용자 여부에 따라 분기처리 필요)
        Interview interview = interviewRepository.save(Interview.builder()
                .member(member)
                .shareRange(true)
                .title(aiStartTtsResponse.getQuestionText())
                .status(InterviewStatus.BEFORE_START)
                .build());
        log.info("인터뷰 세션 생성 성공 interviewId: {}", interview.getId());

        // 인터뷰 시작 처리
        interview.interviewInProgress();

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
                .ttsAudioBase64(aiStartTtsResponse.getTtsAudioBase64())
                .build();
    }


    // 인터뷰 종료 메서드
    @Transactional
    public void exitInterview(String email, Integer interviewId, Integer questionId) {
        // 필요한 인자 -> 사용자, 지금 인터뷰 객체, 지금 질문 객체

        // 필요 도메인 엔티티
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.INTERVIEW_NOT_FOUND));
        InterviewQuestion question = interviewQuestionRepository.findById(questionId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.QUESTION_NOT_FOUND));

        // 현재 저장을 진행중인 질문이 있다면 예외를 발생시킴
        if (question.getStatus() == InterviewStatus.IN_PROGRESS) {
            throw new GeneralException(ErrorStatus.QUESTION_NOT_SAVED);
        }

        // 저장 진행중인 질문이 없다면 아래 로직을 실행함
        // 여기서 ai 호출 -> 제목 및 마지막 답변에 대한 텍스트 변환, 다음 질문 생성
        // 일단 사용자 답변 더미 텍스트 저장
        InterviewAnswer saveAnswer = interviewAnswerRepository.save(InterviewAnswer.builder()
                .interviewQuestion(question)
                .answerAudioUrl("임시 오디오 경로")
                .answerText("임시 답변 텍스트")
                .build());

        // AI 서버에서 반환한 인터뷰 제목으로 업데이트
        interview.interviewFinished("임시 제목 텍스트");
        // todo: 다음 질문 생성 저장 로직 추가
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

}
