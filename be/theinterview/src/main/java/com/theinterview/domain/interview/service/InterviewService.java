package com.theinterview.domain.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theinterview.domain.interview.Repository.InterviewAnswerRepository;
import com.theinterview.domain.interview.Repository.InterviewQuestionRepository;
import com.theinterview.domain.interview.Repository.InterviewRepository;
import com.theinterview.domain.interview.Repository.QuestionCandidateRepository;
import com.theinterview.domain.interview.dto.FastApiRequest.AiNextOpeningRequest;
import com.theinterview.domain.interview.dto.FastApiRequest.AiPipelineReadyDto;
import com.theinterview.domain.interview.dto.FastApiRequest.AiStartTtsRequest;
import com.theinterview.domain.interview.dto.FastApiRequest.AiTitleRequest;
import com.theinterview.domain.interview.dto.FastApiResponse.AiNextOpeningResponse;
import com.theinterview.domain.interview.dto.FastApiResponse.AiPipelineResponse;
import com.theinterview.domain.interview.dto.FastApiResponse.AiStartTtsResponse;
import com.theinterview.domain.interview.dto.FastApiResponse.AiTitleResponse;
import com.theinterview.domain.interview.dto.InterviewRequest.AudioChunkRequest;
import com.theinterview.domain.interview.dto.InterviewRequest.AudioMessageRequest;
import com.theinterview.domain.interview.dto.InterviewRequest.InterviewExitRequest;
import com.theinterview.domain.interview.dto.InterviewRequest.InterviewShareRange;
import com.theinterview.domain.interview.dto.InterviewResponse.FollowUpQuestionDTO;
import com.theinterview.domain.interview.dto.InterviewResponse.InterviewHistory;
import com.theinterview.domain.interview.dto.InterviewResponse.InterviewHistoryDTO;
import com.theinterview.domain.interview.dto.InterviewResponse.InterviewHistoryDetail;
import com.theinterview.domain.interview.dto.InterviewResponse.InterviewHistoryDetailDTO;
import com.theinterview.domain.interview.dto.InterviewResponse.InterviewShareLink;
import com.theinterview.domain.interview.dto.InterviewResponse.SharedInterviewDetail;
import com.theinterview.domain.interview.dto.InterviewResponse.SharedInterviewResponseDTO;
import com.theinterview.domain.interview.dto.InterviewResponse.WebSocketConnectResponse;
import com.theinterview.domain.interview.entity.Interview;
import com.theinterview.domain.interview.entity.InterviewAnswer;
import com.theinterview.domain.interview.entity.InterviewQuestion;
import com.theinterview.domain.interview.entity.QuestionCandidate;
import com.theinterview.domain.interview.enums.InterviewStatus;
import com.theinterview.domain.member.Member;
import com.theinterview.domain.member.repository.MemberRepository;
import com.theinterview.global.config.redis.RedisService;
import com.theinterview.global.response.exception.GeneralException;
import com.theinterview.global.response.status.ErrorStatus;
import com.theinterview.global.util.DomainFindUtils;
import com.theinterview.global.util.FileDeleteUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewService {

    private final MemberRepository memberRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;
    private final InterviewAnswerRepository interviewAnswerRepository;
    private final QuestionCandidateRepository questionCandidateRepository;

    private final AiUtils aiUtils;
    private final FileDeleteUtils fileDeleteUtils;
    private final DomainFindUtils domainFindUtils;
    private final RedisService redisService;

    @Value("${file.audio.base-path}")
    private String answerAudioPath;

    @Value("${file.audio.base-url}")
    private String answerAudioUrl;

    private final ObjectMapper objectMapper;

    // interview 진행 관련 메서드 ----------------------------------------------------------------------------------------------------------------

    // 웹소켓 연결 전, 클라이언트가 구독 할 인터뷰 pk를 발급하고, 첫 질문 텍스트를 TTS로 변환하여 전달함
    // todo: facade 패턴 도입을 통해 메서드 분리 필요(커넥션 풀 고갈 문제 고려)
    @Transactional
    public WebSocketConnectResponse issueWebSocketId(String email) throws JsonProcessingException {

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));

        String dbQuestion = null;

        // 최초 사용자, 기존 사용자 분기처리
        if (member.getIsFirstFinished()) {
            // n + 1 문제 발생 지점 todo: 처리 필요
            List<QuestionCandidate> questionCandidates = member.getQuestionCandidates();
            QuestionCandidate questionCandidate = questionCandidates.get(0);
            // 기존 사용자 처리
            dbQuestion = questionCandidate.getQuestionText();
        }

        // 시작 질문 및 TTS 변환 요청
        AiStartTtsResponse aiStartTtsResponse = aiUtils.callFastApiForStartTts(AiStartTtsRequest
                .builder()
                .dbQuestion(dbQuestion)
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

        // 꼬리 질문 생성 시 필요한 데이터를 redis에서 조회하기 위해 redis에 저장
        AiPipelineReadyDto dto = AiPipelineReadyDto.builder()
                .sequence(1)
                .nickname(member.getNickname())
                .previousQuestion(question.getQuestionText())
                .isFirstFinished(member.getIsFirstFinished())
                .build();

        String redisKey = "interviewId:" + interview.getId() + "questionId:" + question.getId()
                + "aiPipelineReadyDto";

        String dtoJson = objectMapper.writeValueAsString(dto);

        redisService.setValues(redisKey, dtoJson, Duration.ofMinutes(3));

        return WebSocketConnectResponse.builder()
                .interviewId(interview.getId())
                .questionId(question.getId())
                .interviewTitle(question.getQuestionText())
                .ttsAudioBase64(aiStartTtsResponse.getTtsAudioBase64())
                .build();
    }

    /**
     * 상태제어를 통한 DB 락, 오디오 파일을 서버에 저장함(퍼사드에서 호출됨)
     */
    @Transactional
    public AiPipelineReadyDto lockQuestionAndSaveAudio(AudioMessageRequest request,
            byte[] audioBytes)
            throws IOException {

        // 현재 사용자가 답변한 질문 조회
        InterviewQuestion question = interviewQuestionRepository.findById(request.getQuestionId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.QUESTION_NOT_FOUND));

        Interview interview = question.getInterview();

        Member member = interview.getMember();

        // 만약 저장중이라면 409 예외 발생
        if (question.getStatus() == InterviewStatus.IN_PROGRESS) {
            throw new GeneralException(ErrorStatus.QUESTION_NOT_SAVED);
        }

        // 저장 시작 -> DB 락
        question.interviewQuestionInProgress();

        // 파일 저장 후 저장한 경로를 반환
        // 파일 저장 유틸 메서드 호출
        String savedFilePath = saveAnswerFile(audioBytes, interview.getId());

        return AiPipelineReadyDto.builder()
                .savedFilePath(savedFilePath)
                .sequence(question.getSequence() + 1)
                .nickname(member.getNickname())
                .previousQuestion(question.getQuestionText())
                .isFirstFinished(member.getIsFirstFinished())
                .build();
    }

    /**
     * DB에 최종 반환된 질문 텍스트와 audio 파일 경로, 사용자 답변 텍스트를 저장하는 메서드
     */
    @Transactional
    public Integer saveQuestionAndAnswer(Integer interviewId, Integer questionId,
            String generatedQuestionText, String savedPath, String answerText) {

        InterviewQuestion question = domainFindUtils.getInterviewQuestionById(questionId);
        Interview interview = domainFindUtils.getInterviewById(interviewId);

        // DB에 사용자 답변과 오디오 경로 저장
        interviewAnswerRepository.save(InterviewAnswer.builder()
                .interviewQuestion(question)
                .answerText(answerText)
                .answerAudioUrl(savedPath)
                .build());
        log.info("사용자 답변 텍스트 저장 완료");

        // DB에 생성된 꼬리질문 저장
        InterviewQuestion saveQuestion = interviewQuestionRepository.save(
                InterviewQuestion.builder()
                        .interview(interview)
                        .sequence(question.getSequence() + 1)
                        .questionText(generatedQuestionText)
                        .build());

        return saveQuestion.getId();
    }

    /**
     * AI 서버와의 꼬리 질문 생성을 위한 성공적인 통신 이후 관련 반환값들을 DB에 반영하고 DB 락을 해제함 퍼사드에서 호출됨
     */
    @Transactional
    public FollowUpQuestionDTO saveAiResultAndUnlock(AudioMessageRequest request,
            AiPipelineResponse aiPipelineResponse,
            String savedFilePath) {

        InterviewQuestion question = interviewQuestionRepository.findById(request.getQuestionId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.QUESTION_NOT_FOUND));

        Interview interview = question.getInterview();

        Member member = interview.getMember();

        log.info("사용자 답변 텍스트 DB에 저장");
        // InterviewAnswer 엔티티 생성 및 save(생성된 꼬리질문 + savedFilePath) 활용
        InterviewAnswer answer = interviewAnswerRepository.save(
                InterviewAnswer.builder()
                        .interviewQuestion(question)
                        .answerText(aiPipelineResponse.getSttText())
                        .answerAudioUrl(savedFilePath)
                        .build()
        );

        // 최초 사용자이고, 시퀀스가 6 이상이라면 다음 질문이 생성되지 않고 종료됨(최초 사용자 기준)
        if (!member.getIsFirstFinished() && question.getSequence() >= 6) {
            question.interviewQuestionFinished();
            // 생성된 텍스트 및 오디오 파일 반환
            return FollowUpQuestionDTO.builder()
                    .interviewId(interview.getId())
                    .sttResult(aiPipelineResponse.getSttText())
                    .followUpQuestion(aiPipelineResponse.getQuestionText())
                    .ttsAudioBase64(aiPipelineResponse.getTtsAudioBase64())
                    .build();
        }

        // 다음 인터뷰 질문 엔티티 생성 및 저장
        InterviewQuestion generatedQuestion = interviewQuestionRepository.save(
                InterviewQuestion.builder()
                        .interview(interview)
                        .sequence(question.getSequence() + 1)
                        .questionText(aiPipelineResponse.getQuestionText())
                        .build()
        );

        // 저장 완료 후 락 해제
        question.interviewQuestionFinished();

        // 생성된 텍스트 및 오디오 파일 반환
        return FollowUpQuestionDTO.builder()
                .interviewId(interview.getId())
                .sttResult(aiPipelineResponse.getSttText())
                .followUpQuestion(aiPipelineResponse.getQuestionText())
                .ttsAudioBase64(aiPipelineResponse.getTtsAudioBase64())
                .questionId(generatedQuestion.getId())
                .build();
    }


    // 인터뷰 종료 메서드
    // todo: facade 패턴 도입을 통해 메서드 분리 필요(커넥션 풀 고갈 문제 고려)
    @Transactional
    public String exitInterview(InterviewExitRequest request, String email) {
        // 필요한 인자 -> 사용자, 지금 인터뷰 객체, 지금 질문 객체

        // 필요 도메인 엔티티
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));
        Interview interview = interviewRepository.findById(request.getInterviewId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.INTERVIEW_NOT_FOUND));
        InterviewQuestion question = interviewQuestionRepository.findById(request.getQuestionId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.QUESTION_NOT_FOUND));

        // 현재 저장을 진행중인 질문이 있다면 예외를 발생시킴
        if (question.getStatus() == InterviewStatus.IN_PROGRESS) {
            throw new GeneralException(ErrorStatus.QUESTION_NOT_SAVED);
        }

        // 저장 진행중인 질문이 없다면 아래 로직을 실행함
        // 인터뷰의 모든 질문과 답변을 불러와서 조립
        String aiRequest = "";
        int counting = 1;

        List<InterviewQuestion> interviewQuestions = interview.getInterviewQuestions();
        for (InterviewQuestion interviewQuestion : interviewQuestions) {
            InterviewAnswer interviewAnswer = interviewQuestion.getInterviewAnswer();
            // 사용자가 마지막에 답변하지 않고 인터뷰를 종료 했을 경우
            if (interviewAnswer == null) {
                // 해당 질문은 DB에서 삭제함
                interviewQuestionRepository.delete(interviewQuestion);
                continue;
            }
            aiRequest =
                    aiRequest + "Q" + counting + interviewQuestion.getQuestionText() + "\n" + "A"
                            + counting + interviewAnswer.getAnswerText()
                            + "\n"
                            + "\n";

            counting += 1;

        }

        log.info("다음 질문 생성 AI 호출");
        AiNextOpeningResponse aiNextOpeningResponse = aiUtils.callFastApiForNextOpening(
                AiNextOpeningRequest.builder().interviewContext(aiRequest).nickname(
                        member.getNickname()).build());
        // todo: AI 제목 생성 로직 merge 되면 사용
        log.info("이번 인터뷰 제목 요약 AI 호출");
        AiTitleResponse aiTitleResponse = aiUtils.callFastApiForInterviewTitle(
                AiTitleRequest.builder().interviewContext(aiRequest).build());

        // 다음 질문 저장
        questionCandidateRepository.save(QuestionCandidate.builder()
                .questionText(aiNextOpeningResponse.getDbQuestion())
                .member(member)
                .build());

        // 인터뷰 제목 업데이트
        interview.interviewFinished(aiTitleResponse.getTitle());

        // 최초 사용자가 아님을 기록
        member.completeFirstInterview();

        // 인터뷰 제목 반환
        return aiTitleResponse.getTitle();
    }


    /**
     * 음성 바이트 배열을 지정된 경로에 저장하는 유틸 메서드
     */
    @Transactional
    public String saveAnswerFile(byte[] audioBytes, Integer interviewId)
            throws IOException {

        Interview interview = domainFindUtils.getInterviewById(interviewId);

        Member member = interview.getMember();

        String fileName = UUID.randomUUID().toString() + ".webm";

        Path directoryPath = Paths.get(answerAudioPath, String.valueOf(member.getId()),
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

    // 인터뷰 히스토리 조회
    public InterviewHistoryDTO getInterviewHistoryList(Integer memberId, Integer page) {
        // 인터뷰의 상태가 FINISHED인 정보만 5개씩 가져옴
        Pageable pageable = PageRequest.of(page, 5);
        Slice<Interview> interviews = interviewRepository.findAllByMemberIdAndStatus(memberId,
                InterviewStatus.FINISHED, pageable);

        log.info("[InterviewService - getHistoryList] memberID : {} / Load Interviews Size : {}",
                memberId, interviews.getNumberOfElements());

        List<InterviewHistory> interviewHistories = interviews.getContent().stream()
                .map(interview -> InterviewHistory.builder()
                        .interviewId(interview.getId())
                        .interviewTitle(interview.getTitle())
                        .interviewDate(interview.getCreatedAt().toLocalDate())
                        .build())
                .toList();

        return InterviewHistoryDTO.builder()
                .interviews(interviewHistories)
                .hasNext(interviews.hasNext())
                .currentPage(page)
                .build();
    }

    // 인터뷰 히스토리 상세조회
    public InterviewHistoryDetailDTO getInterviewHistoryDetail(Integer interviewId,
            Integer memberId) {
        // 인터뷰 조회
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.INTERVIEW_NOT_FOUND));

        // 요청한 사용자와 인터뷰 진행한 사용자가 다른 경우
        if (!interview.getMember().getId().equals(memberId)) {
            throw new GeneralException(ErrorStatus.FORBIDDEN);
        }

        // 인터뷰 질문 조회
        List<InterviewQuestion> questions = interviewQuestionRepository.findAllByInterviewIdOrderBySequence(
                interviewId);

        // 질문에 맵핑되는 답변 조회
        List<InterviewHistoryDetail> details = questions.stream()
                .map(question -> {
                    InterviewAnswer answer = interviewAnswerRepository
                            .findByInterviewQuestionId(question.getId())
                            .orElseThrow(() -> new GeneralException(ErrorStatus.ANSWER_NOT_FOUND));

                    String dbUrlConverter = answer.getAnswerAudioUrl()
                            .substring(answer.getAnswerAudioUrl().lastIndexOf("/") + 1);

                    return InterviewHistoryDetail.builder()
                            .interviewAnswerId(answer.getId())
                            .interviewQuestion(question.getQuestionText())
                            .interviewText(answer.getAnswerText())
                            .interviewVoice(
                                    answerAudioUrl + "/" + memberId + "/" + interviewId + "/"
                                            + dbUrlConverter)
                            .interviewQAShare(answer.getShareRange())
                            .build();
                }).toList();

        return InterviewHistoryDetailDTO.builder()
                .interviewTitle(interview.getTitle())
                .interviewDate(interview.getCreatedAt().toLocalDate())
                .interviewShare(interview.getShareRange())
                .interview(details)
                .build();
    }

    // 인터뷰 삭제
    @Transactional
    public void deleteInterview(Integer interviewId, Integer memberId) {
        // 인터뷰 조회
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.INTERVIEW_NOT_FOUND));

        // 요청한 사용자와 인터뷰 진행한 사용자가 다른 경우
        if (!interview.getMember().getId().equals(memberId)) {
            throw new GeneralException(ErrorStatus.FORBIDDEN);
        }

        // FK 제약조건으로 인해 자식 테이블부터 순서대로 삭제
        // EC2 파일 삭제
        String dirPath = answerAudioPath + "/" + memberId + "/" + interviewId;
        fileDeleteUtils.deleteDirectory(dirPath);

        // 1. 질문 삭제
        interviewQuestionRepository.deleteAllByInterviewId(interviewId);

        // 2. 인터뷰 삭제
        interviewRepository.delete(interview);
    }

    // 청크된 오디오 데이터를 STT로 변환하기 위해 FastAPI 서버를 호출하는 메서드
    public Mono<Boolean> chunkAudioSTT(AudioChunkRequest request) {

        // 비동기 청킹 요청 및 구독을 통해 해당 메서드에 대한 return이 발생하면 여기서 잡아, redis 저장 로직을 수행함
        return aiUtils.callFastApiForSttChunk(request).map(sttChunkResponse -> {
            // redis 키 저장
            String redisKey = "interviewId:" + request.getInterviewId() + "questionId:"
                    + request.getQuestionId();
            // redis에 청크된 STT 결과, 인덱스 저장
            redisService.addChunkToSortedSet(
                    redisKey + "chunks",
                    sttChunkResponse.getChunkText(),
                    request.getChuckIndex()
            );
            log.info("청킹된 오디오 조각 STT 변환 성공 및 redis에 저장 저장된 텍스트: {}",
                    sttChunkResponse.getChunkText());

            // 청킹이 완료되었다면 해당 질문이 다음 버튼이 눌렸는지 redis에 키를 조회해서 확인함
            if (redisService.hasKey(redisKey + "totalChunks")) {
                // redis에 저장되어 있는 총 청크 수를 조회함
                int totalChunks = Integer.parseInt(
                        redisService.getValues(redisKey + "totalChunks"));
                // 만약 같다면 종료 처리 가능
                log.info("총 청크 개수{}", totalChunks);
                log.info("저장된 청크 개수: {}", redisService.savedChunkCount(redisKey + "chunks"));
                if (totalChunks == redisService.savedChunkCount(redisKey + "chunks")) {
                    return true;
                }
            }
            return false;
        }).onErrorReturn(false);
    }

    // Facade 레이어에서 필요한 정보를 DTO에 담아 반환하는 헬퍼 메서드
    @Transactional(readOnly = true)
    public AiPipelineReadyDto getReadyDto(Integer interviewId, Integer questionId) {

        Interview interview = domainFindUtils.getInterviewById(interviewId);
        InterviewQuestion question = domainFindUtils.getInterviewQuestionById(questionId);

        Member member = interview.getMember();
        return AiPipelineReadyDto.builder()
                .isFirstFinished(member.getIsFirstFinished())
                .previousQuestion(question.getQuestionText())
                .nickname(member.getNickname())
                .sequence(question.getSequence() + 1)
                .build();
    }

    @Transactional
    public void setInterviewShareRange(String userEmail, Integer interviewId,
            InterviewShareRange request) {
        // 사용자 정보 조회
        Member member = memberRepository.findByEmail(userEmail)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));

        // 인터뷰 조회
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.INTERVIEW_NOT_FOUND));

        // 요청한 사용자와 인터뷰 진행한 사용자가 다른 경우
        if (!interview.getMember().getId().equals(member.getId())) {
            throw new GeneralException(ErrorStatus.FORBIDDEN);
        }

        // Request로 오는 값이 true라면 shareRange 수정 후 UUID 생성 / false라면 shareRange 수정 후 UUID 삭제
        interview.setShareRange(request.getInterviewShareRange());
    }

    // 인터뷰 상세페이지 공유 링크 발급
    public InterviewShareLink getInterviewShareLink(String userEmail, Integer interviewId) {
        // 사용자 정보 조회
        Member member = memberRepository.findByEmail(userEmail)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));

        // 인터뷰 조회
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.INTERVIEW_NOT_FOUND));

        // 요청한 사용자와 인터뷰 진행한 사용자가 다른 경우
        if (!interview.getMember().getId().equals(member.getId())) {
            throw new GeneralException(ErrorStatus.FORBIDDEN);
        }

        // ShareLink가 없거나 공개범위가 비공개면 예외처리
        if (interview.getShareLink() == null || Boolean.FALSE.equals(interview.getShareRange())) {
            throw new GeneralException(ErrorStatus.SHARE_NOT_ENABLED);
        }
        return InterviewShareLink.builder()
                .shareLink("https://j14a307.p.ssafy.io/interview/share/" + interview.getShareLink())
                .build();
    }

    // 공유된 인터뷰 조회
    // 인터뷰 히스토리 상세조회
    public SharedInterviewResponseDTO getSharedInterviewDetail(String shareUuid) {
        // UUID로 인터뷰 조회
        Interview interview = interviewRepository.findByShareLink(shareUuid)
                .orElseThrow(() -> new GeneralException(ErrorStatus.INTERVIEW_NOT_FOUND));

        // 공유 상태가 true인지 확인
        if (!Boolean.TRUE.equals(interview.getShareRange())) {
            throw new GeneralException(ErrorStatus.SHARE_NOT_ENABLED);
        }

        // 인터뷰 질문 조회
        List<InterviewQuestion> questions = interviewQuestionRepository.findAllByInterviewIdOrderBySequence(
                interview.getId());

        // 질문에 맵핑되는 답변 조회
        List<SharedInterviewDetail> details = questions.stream()
                .map(question -> {
                    InterviewAnswer answer = interviewAnswerRepository
                            .findByInterviewQuestionId(question.getId())
                            .orElseThrow(() -> new GeneralException(ErrorStatus.ANSWER_NOT_FOUND));

                    String dbUrlConverter = answer.getAnswerAudioUrl()
                            .substring(answer.getAnswerAudioUrl().lastIndexOf("/") + 1);

                    return SharedInterviewDetail.builder()
                            .interviewQuestion(question.getQuestionText())
                            .interviewText(answer.getAnswerText())
                            // TODO: 오디오 파일 변경방식 변경 예정으로 우선 형태를 대충 작성
                            .interviewVoice(
                                    answerAudioUrl + "/" + dbUrlConverter)
                            .build();
                }).toList();

        return SharedInterviewResponseDTO.builder()
                .interviewTitle(interview.getTitle())
                .interviewDate(interview.getCreatedAt().toLocalDate())
                .interview(details)
                .build();
    }
}
