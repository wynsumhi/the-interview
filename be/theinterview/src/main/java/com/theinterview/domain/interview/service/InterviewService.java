package com.theinterview.domain.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theinterview.domain.interview.Repository.InterviewAnswerRepository;
import com.theinterview.domain.interview.Repository.InterviewQuestionRepository;
import com.theinterview.domain.interview.Repository.InterviewRepository;
import com.theinterview.domain.interview.Repository.QuestionCandidateRepository;
import com.theinterview.domain.interview.dto.AiRagDto.Turn;
import com.theinterview.domain.interview.dto.FacadeInnerDto.AnswerTts;
import com.theinterview.domain.interview.dto.FastApiRequest.AiNextOpeningRequest;
import com.theinterview.domain.interview.dto.FastApiRequest.AiPipelineReadyDto;
import com.theinterview.domain.interview.dto.FastApiRequest.AiTitleRequest;
import com.theinterview.domain.interview.dto.FastApiResponse.AiNextOpeningResponse;
import com.theinterview.domain.interview.dto.FastApiResponse.AiPipelineResponse;
import com.theinterview.domain.interview.dto.FastApiResponse.AiPipelineResponse.InterviewStateDb.FusedScores;
import com.theinterview.domain.interview.dto.FastApiResponse.AiStartTtsResponse;
import com.theinterview.domain.interview.dto.FastApiResponse.AiTitleResponse;
import com.theinterview.domain.interview.dto.FastApiResponse.LastAnswerEmotionResponse;
import com.theinterview.domain.interview.dto.FastApiResponse.LastAnswerTextResponse;
import com.theinterview.domain.interview.dto.InterviewRequest.AudioChunkRequest;
import com.theinterview.domain.interview.dto.InterviewRequest.AudioMessageRequest;
import com.theinterview.domain.interview.dto.InterviewRequest.BookmarkSetting;
import com.theinterview.domain.interview.dto.InterviewRequest.InterviewExitRequest;
import com.theinterview.domain.interview.dto.InterviewRequest.InterviewShareRange;
import com.theinterview.domain.interview.dto.InterviewResponse.FollowUpQuestionDTO;
import com.theinterview.domain.interview.dto.InterviewResponse.InterviewExploreDetail;
import com.theinterview.domain.interview.dto.InterviewResponse.InterviewExploreResponse;
import com.theinterview.domain.interview.dto.InterviewResponse.InterviewHistory;
import com.theinterview.domain.interview.dto.InterviewResponse.InterviewHistoryDTO;
import com.theinterview.domain.interview.dto.InterviewResponse.InterviewHistoryDetail;
import com.theinterview.domain.interview.dto.InterviewResponse.InterviewHistoryDetailDTO;
import com.theinterview.domain.interview.dto.InterviewResponse.InterviewPostItBoardResponse;
import com.theinterview.domain.interview.dto.InterviewResponse.InterviewPostItDetail;
import com.theinterview.domain.interview.dto.InterviewResponse.InterviewShareLink;
import com.theinterview.domain.interview.dto.InterviewResponse.InterviewUnfinishedResponse;
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
import com.theinterview.domain.shareboard.entity.ShareBoard;
import com.theinterview.domain.shareboard.entity.ShareBoardPostitLayout;
import com.theinterview.domain.shareboard.enums.ShareBoardPostitType;
import com.theinterview.domain.shareboard.repository.ShareBoardPostitLayoutRepository;
import com.theinterview.domain.shareboard.repository.ShareBoardRepository;
import com.theinterview.global.config.redis.RedisService;
import com.theinterview.global.response.exception.GeneralException;
import com.theinterview.global.response.status.ErrorStatus;
import com.theinterview.global.util.DomainFindUtils;
import com.theinterview.global.util.FileDeleteUtils;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
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
    private final ShareBoardRepository shareBoardRepository;
    private final ShareBoardPostitLayoutRepository shareBoardPostitLayoutRepository;

    @Value("${file.audio.base-path}")
    private String answerAudioPath;

    private final ObjectMapper objectMapper;

    /*
     * 정제 답변 텍스트는 꼬리질문 응답보다 먼저 도착할 수 있으므로,
     * 실제 InterviewAnswer 행이 만들어지기 전까지는 Redis에 임시 보관한다.
     * TTL을 너무 짧게 두면 네트워크 지연 상황에서 merge 전에 사라질 수 있고,
     * 너무 길게 두면 불필요한 임시 데이터가 오래 남으므로 인터뷰 한 턴 처리 시간을 감안해 10분으로 둔다.
     */
    private static final Duration REFINED_ANSWER_TEMP_TTL = Duration.ofMinutes(10);

    // interview 진행 관련 메서드 ----------------------------------------------------------------------------------------------------------------

    // 사용자의 첫 질문 요청이 시작되면 인터뷰 객체와 첫번째 질문을 만들어 반환함
    @Transactional
    public WebSocketConnectResponse saveInterviewAndQuestion(
            Member member,
            AiStartTtsResponse aiStartTtsResponse,
            String voice,
            Boolean restartFlag,
            Integer interviewId
    )
            throws JsonProcessingException {

        Interview interview;
        InterviewQuestion question;

        if (Boolean.TRUE.equals(restartFlag)) {
            // 비정상 종료 이어서 하기
            interview = domainFindUtils.getInterviewById(interviewId);
            question = interviewQuestionRepository.findTopByInterviewIdOrderBySequenceDesc(
                            interviewId)
                    .orElseThrow(() -> new GeneralException(ErrorStatus.QUESTION_NOT_FOUND));

            // 상태 확인 및 업데이트 (필요시)
            interview.interviewInProgress();
        } else {
            // 기존 로직
            // interview 객체 생성
            interview = interviewRepository.save(Interview.builder()
                    .member(member)
                    .shareRange(true) // 기본 공개 설정
                    .title(aiStartTtsResponse.getQuestionText())
                    .status(InterviewStatus.BEFORE_START)
                    .build());
            log.info("인터뷰 세션 생성 성공 interviewId: {}", interview.getId());

            // 인터뷰 진행중으로 상태 변경
            interview.interviewInProgress();

            // 첫 질문 DB에 저장
            question = interviewQuestionRepository.save(
                    InterviewQuestion.builder()
                            .interview(interview)
                            .sequence(1)
                            .questionText(interview.getTitle())
                            .build());
        }

        AiPipelineReadyDto dto = AiPipelineReadyDto.builder()
                .sequence(question.getSequence())
                .nickname(member.getNickname())
                .previousQuestion(question.getQuestionText())
                .isFirstFinished(member.getIsFirstFinished())
                .voice(voice)
                .build();

        String redisKey = "interviewId:" + interview.getId() + "questionId:" + question.getId()
                + "aiPipelineReadyDto";

        redisService.setValues(redisKey, objectMapper.writeValueAsString(dto),
                Duration.ofMinutes(3));

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
        AiPipelineReadyDto previousReadyDto = getReadyDto(
                request.getInterviewId(),
                request.getQuestionId()
        );

        return AiPipelineReadyDto.builder()
                .savedFilePath(savedFilePath)
                .sequence(question.getSequence() + 1)
                .nickname(member.getNickname())
                .previousQuestion(question.getQuestionText())
                .isFirstFinished(member.getIsFirstFinished())
                .voice(previousReadyDto.getVoice())
                .build();
    }

    /**
     * DB에 최종 반환된 질문 텍스트와 audio 파일 경로, 사용자 답변 텍스트를 저장하는 메서드
     */
    @Transactional
    public Integer saveQuestionAndAnswer(Integer interviewId, Integer questionId,
            String generatedQuestionText, String savedPath, String answerText,
            FusedScores fusedScores) {

        InterviewQuestion question = domainFindUtils.getInterviewQuestionById(questionId);
        Interview interview = domainFindUtils.getInterviewById(interviewId);
        String resolvedAnswerText = resolveAnswerText(interviewId, questionId, answerText);

        // DB에 사용자 답변과 오디오 경로 저장
        interviewAnswerRepository.save(InterviewAnswer.builder()
                .interviewQuestion(question)
                .answerText(resolvedAnswerText)
                .answerAudioUrl(savedPath)
                .calmScore(conversionEmotionScore(fusedScores.getCalm()))
                .engagedScore(conversionEmotionScore(fusedScores.getEngaged()))
                .lowScore(conversionEmotionScore(fusedScores.getLow()))
                .anxiousScore(conversionEmotionScore(fusedScores.getAnxious()))
                .tenseScore(conversionEmotionScore(fusedScores.getTense()))
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

    // 감정 점수가 null이면 0.00 변환
    private BigDecimal conversionEmotionScore(BigDecimal score) {
        if (score == null) {
            return BigDecimal.ZERO;
        }
        return score;
    }

    private String buildRefinedAnswerRedisKey(Integer interviewId, Integer questionId) {
        /*
         * interviewId와 questionId를 함께 키에 포함해서
         * 동일 사용자가 여러 인터뷰를 동시에 진행하거나,
         * 질문 번호가 우연히 겹치는 경우에도 충돌 없이 임시 텍스트를 분리한다.
         */
        return "interviewId:" + interviewId + "questionId:" + questionId + "refinedAnswer";
    }

    @Transactional
    public void saveRefinedAnswer(Integer interviewId, Integer questionId, String refinedAnswer) {
        /*
         * 내부 콜백 요청은 사용자 입력이 아니라 AI 서버가 보내는 서버 간 요청이다.
         * 그래도 questionId가 실제 interviewId에 속한 질문인지 검증해 두어야
         * 잘못된 매핑으로 다른 인터뷰 답변이 덮어써지는 사고를 막을 수 있다.
         */
        InterviewQuestion question = domainFindUtils.getInterviewQuestionById(questionId);
        if (!question.getInterview().getId().equals(interviewId)) {
            throw new GeneralException(ErrorStatus.FORBIDDEN);
        }

        String normalizedRefinedAnswer = refinedAnswer == null ? "" : refinedAnswer.trim();
        if (normalizedRefinedAnswer.isBlank()) {
            throw new GeneralException(ErrorStatus.BAD_REQUEST);
        }

        InterviewAnswer existingAnswer = interviewAnswerRepository.findByInterviewQuestionId(questionId)
                .orElse(null);

        if (existingAnswer != null) {
            /*
             * 꼬리질문 응답 저장이 먼저 끝난 경우에는 이미 InterviewAnswer 행이 존재한다.
             * 이때는 Redis를 거치지 않고 DB 텍스트만 즉시 갱신하는 것이 가장 단순하고 빠르다.
             */
            existingAnswer.updateAnswerText(normalizedRefinedAnswer);
            redisService.deleteValues(buildRefinedAnswerRedisKey(interviewId, questionId));
            return;
        }

        /*
         * 반대로 정제 텍스트가 먼저 도착한 경우에는 아직 저장 대상 행이 없으므로
         * Redis에 임시 보관해 두었다가 이후 saveQuestionAndAnswer / saveAnswer 시점에 merge한다.
         */
        redisService.setValues(
                buildRefinedAnswerRedisKey(interviewId, questionId),
                normalizedRefinedAnswer,
                REFINED_ANSWER_TEMP_TTL
        );
    }

    private String resolveAnswerText(Integer interviewId, Integer questionId, String fallbackAnswerText) {
        /*
         * 답변을 최종 저장하는 모든 지점에서 동일한 규칙으로 텍스트를 결정하기 위한 공통 메서드다.
         * 우선순위는 "Redis에 대기 중인 정제 텍스트 > 현재 전달된 fallback 원문" 순서다.
         * Redis 값을 사용한 경우 즉시 삭제해서 같은 임시 텍스트가 다음 질문에 재사용되지 않게 한다.
         */
        String redisKey = buildRefinedAnswerRedisKey(interviewId, questionId);
        String refinedAnswer = redisService.getValues(redisKey);
        if (refinedAnswer != null && !refinedAnswer.isBlank()) {
            redisService.deleteValues(redisKey);
            return refinedAnswer;
        }

        /*
         * 기본 pipeline 경로처럼 AI 서버가 STT부터 모두 처리하는 흐름에서는
         * 백엔드가 fallback 원문을 직접 갖고 있지 않을 수 있다.
         * 이 경우 콜백이 거의 동시에 도착했지만 아직 Redis 반영 전일 가능성이 있으므로
         * 짧게 재확인해 보고, 그래도 없으면 마지막 fallback 값으로 저장한다.
         */
        if (fallbackAnswerText == null || fallbackAnswerText.isBlank()) {
            for (int retry = 0; retry < 5; retry++) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                refinedAnswer = redisService.getValues(redisKey);
                if (refinedAnswer != null && !refinedAnswer.isBlank()) {
                    redisService.deleteValues(redisKey);
                    return refinedAnswer;
                }
            }
        }
        return fallbackAnswerText;
    }

    public String getSavedAnswerText(Integer questionId) {
        /*
         * 꼬리질문 응답 DTO에는 사용자에게 보여 줄 최신 답변 텍스트가 필요하다.
         * saveQuestionAndAnswer가 내부적으로 Redis merge를 수행한 뒤 저장하므로,
         * 응답 직전에는 DB에서 다시 읽어 오는 값이 가장 최종 상태에 가깝다.
         */
        return interviewAnswerRepository.findByInterviewQuestionId(questionId)
                .map(InterviewAnswer::getAnswerText)
                .orElse("");
    }

    @Transactional
    public void saveAnswer(InterviewQuestion interviewQuestion, String answerText,
            String savedPath) {
        String resolvedAnswerText = resolveAnswerText(
                interviewQuestion.getInterview().getId(),
                interviewQuestion.getId(),
                answerText
        );

        // DB에 사용자의 마지막 답변 저장
        interviewAnswerRepository.save(InterviewAnswer.builder()
                .interviewQuestion(interviewQuestion)
                .answerText(resolvedAnswerText)
                .answerAudioUrl(savedPath)
                .build());
    }

    @Transactional
    public InterviewQuestion saveAnswer(Integer questionId, String answerText,
            String savedPath) {

        InterviewQuestion interviewQuestion = domainFindUtils.getInterviewQuestionById(questionId);
        String resolvedAnswerText = resolveAnswerText(
                interviewQuestion.getInterview().getId(),
                questionId,
                answerText
        );

        // DB에 사용자의 마지막 답변 저장
        interviewAnswerRepository.save(InterviewAnswer.builder()
                .interviewQuestion(interviewQuestion)
                .answerText(resolvedAnswerText)
                .answerAudioUrl(savedPath)
                .build());
        return interviewQuestion;
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
        String resolvedAnswerText = resolveAnswerText(
                request.getInterviewId(),
                request.getQuestionId(),
                ""
        );

        log.info("사용자 답변 텍스트 DB에 저장");
        // InterviewAnswer 엔티티 생성 및 save(생성된 꼬리질문 + savedFilePath) 활용
        InterviewAnswer answer = interviewAnswerRepository.save(
                InterviewAnswer.builder()
                        .interviewQuestion(question)
                        .answerText(resolvedAnswerText)
                        .answerAudioUrl(savedFilePath)
                        .build()
        );

        // 최초 사용자이고, 시퀀스가 6 이상이라면 다음 질문이 생성되지 않고 종료됨(최초 사용자 기준)
        if (!member.getIsFirstFinished() && question.getSequence() >= 6) {
            question.interviewQuestionFinished();
            // 생성된 텍스트 및 오디오 파일 반환
            return FollowUpQuestionDTO.builder()
                    .interviewId(interview.getId())
                    .sttResult(answer.getAnswerText())
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
                .sttResult(answer.getAnswerText())
                .followUpQuestion(aiPipelineResponse.getQuestionText())
                .ttsAudioBase64(aiPipelineResponse.getTtsAudioBase64())
                .questionId(generatedQuestion.getId())
                .build();
    }


    // 인터뷰 종료 시 facade에서 호출되는 메서드
    // 다음 질문 생성과 제목 생성을 위해 해당 인터뷰에서의 사용자의 이전 질문과 답변 쌍을 조합하여 반환함
    @Transactional
    public AnswerTts findDomainMakeQnACouple(String email, Integer interviewId) {

        // 필요 도메인 조회
        Interview interview = domainFindUtils.getInterviewById(interviewId);

        Member member = interview.getMember();
        // 권한 확인
        if (!member.getEmail().equals(email)) {
            throw new GeneralException(ErrorStatus.FORBIDDEN);
        }

        // 해당 인터뷰에서의 모둔 사용자 질문과 답변 쌍 조합
        // 저장 진행중인 질문이 없다면 아래 로직을 실행함
        // 인터뷰의 모든 질문과 답변을 불러와서 조립
        String aiRequest = "";
        int counting = 1;

        // rag 저장에 사용할 turn dto
        List<Turn> turns = new ArrayList<>();

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

            // turns 객체 조립
            turns.add(Turn.builder()
                    .questionId(String.valueOf(interviewQuestion.getId()))
                    .answerId(String.valueOf(interviewAnswer.getId()))
                    .question(interviewQuestion.getQuestionText())
                    .answer(interviewAnswer.getAnswerText())
                    .turnIndex(counting)
                    // todo: 메타데이터(감정) merge 되면 추가
                    .metadata(Map.of())
                    .build());

            counting += 1;

        }

        return AnswerTts.builder()
                .QnACouple(aiRequest)
                .member(member)
                .turns(turns)
                .build();
    }

    // 사용자의 질문/답변 쌍을 하나의 문자열로 조합하여 반환하는 메서드
    @Transactional(readOnly = true)
    public String makeAnswerQuestionCouple(Integer interviewId) {

        Interview interview = domainFindUtils.getInterviewById(interviewId);

        List<InterviewQuestion> interviewQuestions = interview.getInterviewQuestions();

        String context = "";

        int count = 1;

        for (InterviewQuestion interviewQuestion : interviewQuestions) {

            if (interviewQuestion.getInterviewAnswer() == null) {
                continue;
            }

            context =
                    context + "Q" + count + ": " + interviewQuestion.getQuestionText() + "\n" + "A"
                            + count + ": " + interviewQuestion.getInterviewAnswer().getAnswerText()
                            + "\n";
            count += 1;
        }
        return context;
    }

    // 인터뷰 종료 시 facade에서 호출되는 메서드
    // 다음 질문과 제목이 AI에게서 생성이 완료되면 DB에 해당 값을 저장하기 위해 호출됨
    @Transactional
    public void saveExitAiResponse(Integer interviewId, String title,
            String nextStartQuestion) {

        // DB에 해당 값들을 모두 저장함
        Interview interview = domainFindUtils.getInterviewById(interviewId);

        Member member = interview.getMember();

        questionCandidateRepository.save(QuestionCandidate.builder()
                .member(member)
                .questionText(nextStartQuestion)
                .build());

        // 완료처리
        member.completeFirstInterview();
        interview.interviewFinished(title);
    }

    public void saveRequiredFlagInRedis(String savedPath, String redisKey, String totalChunk) {

        // 여기서 바로 총 청크 수를 저장함
        redisService.setValues(redisKey + "totalChunks", totalChunk,
                Duration.ofMinutes(3)); // 유효 기간 3분
        // 음성 파일 저장 경로 redis에 저장
        redisService.setValues(redisKey + "savedPath", savedPath, Duration.ofMinutes(3));
    }

    // 인터뷰 종료 메서드
    // todo: 실시간 STT 청크 프론트 로직 완료되면 삭제
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

                    String dbUrl = answer.getAnswerAudioUrl()
                            .substring(answer.getAnswerAudioUrl().lastIndexOf("/") + 1);
                    String dbUrlConverter = dbUrl.substring(0, dbUrl.lastIndexOf("."));

                    return InterviewHistoryDetail.builder()
                            .interviewAnswerId(answer.getId())
                            .interviewQuestion(question.getQuestionText())
                            .interviewText(answer.getAnswerText())
                            .interviewVoice(dbUrlConverter)
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
                            request.getChunkIndex()
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
                }).doOnError(e -> log.error("청크 변환 실패: {}", e.getMessage()))
                .onErrorReturn(false);
    }

    // Facade 레이어에서 필요한 정보를 DTO에 담아 반환하는 헬퍼 메서드
    @Transactional(readOnly = true)
    public AiPipelineReadyDto getReadyDto(Integer interviewId, Integer questionId) {

        String redisKey =
                "interviewId:" + interviewId + "questionId:" + questionId + "aiPipelineReadyDto";
        String dtoJson = redisService.getValues(redisKey);
        if (dtoJson != null && !dtoJson.isBlank()) {
            try {
                return objectMapper.readValue(dtoJson, AiPipelineReadyDto.class);
            } catch (JsonProcessingException e) {
                log.warn("AiPipelineReadyDto 역직렬화에 실패해 DB fallback을 사용합니다.", e);
            }
        }

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

                    String dbUrl = answer.getAnswerAudioUrl()
                            .substring(answer.getAnswerAudioUrl().lastIndexOf("/") + 1);
                    String dbUrlConverter = dbUrl.substring(0, dbUrl.lastIndexOf("."));

                    return SharedInterviewDetail.builder()
                            .interviewQuestion(question.getQuestionText())
                            .interviewText(answer.getAnswerText())
                            .interviewVoice(dbUrlConverter)
                            .build();
                }).toList();

        return SharedInterviewResponseDTO.builder()
                .interviewTitle(interview.getTitle())
                .interviewDate(interview.getCreatedAt().toLocalDate())
                .interview(details)
                .build();
    }

    // 사용자 공유 페이지 ( 포스트 잇 )
    public InterviewPostItBoardResponse getSharedPostItBoard(String userUuid, Integer page) {
        // userUuid로 보드 주인 조회
        Member owner = memberRepository.findByUserUuid(userUuid)
                .orElseThrow(() -> new GeneralException(ErrorStatus.MEMBER_NOT_FOUND));
        // 12개씩 가져오며 업데이트 날짜를 기준으로 가져옴
        Pageable pageable = PageRequest.of(page, 12, Sort.by("createdAt").descending());
        Slice<InterviewAnswer> answerSlice = interviewAnswerRepository.findAllByMemberUUIDAndShared(
                userUuid, pageable);

        // 북마크된 답변을 포스트잇 DTO 목록으로 변환
        List<InterviewPostItDetail> postItList = answerSlice.getContent().stream()
                .map(InterviewPostItDetail::from)
                .toList();

        return InterviewPostItBoardResponse.builder()
                // 보드 주인 식별 정보
                .ownerUserUuid(owner.getUserUuid())
                .ownerMemberId(owner.getId())
                .ownerNickname(owner.getNickname())
                // 페이지 정보와 포스트잇 목록
                .currentPage(page)
                .postIts(postItList)
                .hasNext(answerSlice.hasNext())
                .build();
    }

    // 인터뷰 탐색하기
    public InterviewExploreResponse getInterviewExplore(Integer page) {
        // 인터뷰의 상태가 FINISHED인 정보만 5개씩 가져오며 업데이트 날짜를 기준으로 가져옴
        Pageable pageable = PageRequest.of(page, 12, Sort.by("updatedAt").descending());
        Slice<Interview> interviews = interviewRepository.findAllByStatusAndShareRange(
                InterviewStatus.FINISHED, true, pageable);

        List<InterviewExploreDetail> detailList = interviews.getContent().stream()
                .map(interview -> {
                    // 해당 인터뷰에 해당하는 질문들을 가져옴
                    List<InterviewQuestion> questions = interview.getInterviewQuestions();

                    // 질문이 없거나 첫 질문에 답변이 없는 인터뷰는 스킵
                    if (questions.isEmpty() || questions.get(0).getInterviewAnswer() == null) {
                        return null;
                    }

                    // 첫 질문에 해당하는 답변을 대표 답변으로 지정
                    String representAnswer = questions.get(0).getInterviewAnswer().getAnswerText();
                    Integer answerCount = questions.size();

                    return InterviewExploreDetail.builder()
                            .interviewAnswerCount(answerCount)
                            .interviewId(interview.getId())
                            .interviewDate(interview.getCreatedAt().toLocalDate())
                            .interviewTitle(interview.getTitle())
                            .answerText(representAnswer)
                            // TODO : 이미지화 시키기 위한, 감정 지표에 대한 [] 내용 추후 수정필요
                            .fusedScores("임의 데이터")
                            .shareUuid(interview.getShareLink())
                            .build();
                })
                .toList();

        return InterviewExploreResponse.builder()
                .interviews(detailList)
                .hasNext(interviews.hasNext())
                .currentPage(page)
                .build();
    }

    // 인터뷰 북마크 추가/삭제
    @Transactional
    public void setBookmark(String userEmail, Integer interviewId, BookmarkSetting request) {
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

        // 특정 답변 조회
        InterviewAnswer interviewAnswer = interviewAnswerRepository.findById(
                        request.getInterviewAnswerID())
                .orElseThrow(() -> new GeneralException(ErrorStatus.ANSWER_NOT_FOUND));

        // Request로 오는 값이 true라면 shareRange 수정 / false라면 shareRange 수정
        // 답변의 북마크 공유 여부를 갱신합니다.
        interviewAnswer.updateShareRange(request.getInterviewShareRange());
        if (Boolean.TRUE.equals(request.getInterviewShareRange())) {
            // 북마크가 공유 상태로 바뀌면 공유 보드용 레이아웃도 함께 보장합니다.
            ensureBookmarkLayout(interviewAnswer);
        }
    }

    private void ensureBookmarkLayout(InterviewAnswer answer) {
        // 북마크 원본 답변의 주인을 기준으로 연결할 공유 보드를 찾습니다.
        ShareBoard shareBoard = shareBoardRepository.findByOwnerId(
                        answer.getInterviewQuestion().getInterview().getMember().getId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.SHARE_BOARD_NOT_FOUND));

        // 이미 레이아웃이 있으면 그대로 두고, 없으면 기본 레이아웃을 생성합니다.
        shareBoardPostitLayoutRepository
                .findByShareBoardIdAndPostitTypeAndSourceId(shareBoard.getId(),
                        ShareBoardPostitType.BOOKMARK, answer.getId())
                .orElseGet(
                        () -> shareBoardPostitLayoutRepository.save(ShareBoardPostitLayout.builder()
                                .shareBoard(shareBoard)
                                .postitType(ShareBoardPostitType.BOOKMARK)
                                .sourceId(answer.getId())
                                .colorCode(resolveBookmarkColor(answer.getId()))
                                .rotationDeg(defaultRotation(answer.getId()))
                                .positionX(defaultPositionX(answer.getId()))
                                .positionY(defaultPositionY(answer.getId()))
                                .zIndex(0)
                                .build()));
    }

    private String resolveBookmarkColor(Integer sourceId) {
        // 북마크 기본 색상은 시스템에서 임의로 하나 고정 배정합니다.
        return "#F3C5C5";
    }

    private BigDecimal defaultRotation(Integer sourceId) {
        // 기본 회전값은 -3으로 시작합니다.
        return BigDecimal.valueOf(-3);
    }

    private BigDecimal defaultPositionX(Integer sourceId) {
        // 기본 X 좌표는 0으로 시작합니다.
        return BigDecimal.ZERO;
    }

    private BigDecimal defaultPositionY(Integer sourceId) {
        // 기본 Y 좌표는 0으로 시작합니다.
        return BigDecimal.ZERO;
    }

    // 사용자 인터뷰 오디오 재생
    public Resource getVoiceResource(String email, String voiceUuid) {
        // DB에 UUID에 해당하는 정보가 있는지 조회
        InterviewAnswer answer = interviewAnswerRepository.findByVoiceUuidInPath(voiceUuid)
                .orElseThrow(() -> new GeneralException(ErrorStatus.VOICE_NOT_FOUND));

        // 이메일이 존재하고, 해당 답변의 주인 이메일과 일치하는지 확인
        Member owner = answer.getInterviewQuestion().getInterview().getMember();
        boolean isOwner = (email != null) && owner.getEmail().equals(email);

        // Interview 공개범위 확인
        InterviewQuestion interviewQuestion = answer.getInterviewQuestion();
        Interview interview = interviewQuestion.getInterview();

        // 공개 설정이 되어 있지 않고, 주인도 아니라면 예외처리
        if (!interview.getShareRange() && !answer.getShareRange() && !isOwner) {
            throw new GeneralException(ErrorStatus.FORBIDDEN);
        }

        Integer memberId = owner.getId();
        Integer interviewId = answer.getInterviewQuestion().getInterview().getId();

        String fullPath = Paths.get(answerAudioPath, memberId.toString(), interviewId.toString(),
                voiceUuid + ".webm").toString();

        File file = new File(fullPath);
        Resource resource = new FileSystemResource(file);

        if (!resource.exists()) {
            throw new GeneralException(ErrorStatus.VOICE_NOT_FOUND);
        }

        return resource;
    }

    // 키워드 저장 메서드
    @Transactional
    public void saveKeyword(InterviewQuestion question, String keyword) {

        // 영속성 컨텍스트에 다시 올리기 위해 조회
        InterviewQuestion findQuestion = domainFindUtils.getInterviewQuestionById(question.getId());

        InterviewAnswer answer = findQuestion.getInterviewAnswer();

        answer.updateKeyword(keyword);
        log.info("키워드 저장 성공. 키워드: {}", keyword);
    }

    // 완료되지 않고 종료된 인터뷰가 있는지 확인
    public InterviewUnfinishedResponse findUnfinishedInterviews(String email) {

        Member member = domainFindUtils.getMemberByEmail(email);
        // TODO : 인터뷰를 정상종료 하더라도 바로 FINISHED로 변경되지 않기 때문에 인터뷰 시작시 비정상 로직이 있다고 뜰 수 있음.

        // 종료가 되지 않은 인터뷰가 있는지 찾음
        Integer interviewId = interviewRepository.findTopByMemberIdAndStatusNotOrderByUpdatedAtDesc(
                        member.getId(), InterviewStatus.FINISHED)
                .map(Interview::getId)
                .orElse(null);

        return InterviewUnfinishedResponse.builder()
                .unfinishedInterviewId(interviewId)
                .build();
    }

    // 종료 직전 마지막 답변의 감정 상태, 정제 텍스트를 저장하는 메서드
    @Transactional
    public void saveEmotionTts(LastAnswerTextResponse lastAnswerTextResponse,
            LastAnswerEmotionResponse lastAnswerEmotionResponse, InterviewExitRequest request) {

        // 변경 할 DB 조회
        InterviewQuestion question = domainFindUtils.getInterviewQuestionById(
                request.getQuestionId());

        InterviewAnswer interviewAnswer = question.getInterviewAnswer();

        interviewAnswer.updateTextEmotion(lastAnswerTextResponse.getRefinedAnswer(),
                lastAnswerEmotionResponse.getInterviewStateDb());

    }
}
