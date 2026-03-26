package com.theinterview.domain.interview.facade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theinterview.domain.interview.Repository.InterviewQuestionRepository;
import com.theinterview.domain.interview.dto.AiRagDto.DeleteRequest;
import com.theinterview.domain.interview.dto.AiRagDto.Turn;
import com.theinterview.domain.interview.dto.AiRagDto.UpsertRequest;
import com.theinterview.domain.interview.dto.FastApiRequest.AiNextOpeningRequest;
import com.theinterview.domain.interview.dto.FastApiRequest.AiPipelineReadyDto;
import com.theinterview.domain.interview.dto.FastApiRequest.AiStartTtsRequest;
import com.theinterview.domain.interview.dto.FastApiRequest.AiTitleRequest;
import com.theinterview.domain.interview.dto.FastApiRequest.KeywordRequest;
import com.theinterview.domain.interview.dto.FastApiResponse.AiNextOpeningResponse;
import com.theinterview.domain.interview.dto.FastApiResponse.AiPipelineResponse;
import com.theinterview.domain.interview.dto.FastApiResponse.AiTitleResponse;
import com.theinterview.domain.interview.dto.InterviewRequest.AudioMessageRequest;
import com.theinterview.domain.interview.dto.InterviewRequest.InterviewExitRequest;
import com.theinterview.domain.interview.dto.InterviewRequest.TotalChunkRequest;
import com.theinterview.domain.interview.dto.InterviewRequest.restartInterviewRequest;
import com.theinterview.domain.interview.dto.InterviewResponse.FollowUpQuestionDTO;
import com.theinterview.domain.interview.dto.InterviewResponse.WebSocketConnectResponse;
import com.theinterview.domain.interview.entity.InterviewQuestion;
import com.theinterview.domain.interview.entity.QuestionCandidate;
import com.theinterview.domain.interview.service.AiUtils;
import com.theinterview.domain.interview.service.InterviewService;
import com.theinterview.domain.member.Member;
import com.theinterview.global.config.redis.RedisService;
import com.theinterview.global.response.exception.GeneralException;
import com.theinterview.global.response.status.ErrorStatus;
import com.theinterview.global.util.DomainFindUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.View;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@RequiredArgsConstructor
@Slf4j
public class InterviewFacade {

    private final InterviewService interviewService;
    private final AiUtils aiUtils;
    private final RedisService redisService;
    private final DomainFindUtils domainFindUtils;

    private final ObjectMapper objectMapper;
    private final View error;
    private final SimpMessagingTemplate messagingTemplate;
    private final InterviewQuestionRepository interviewQuestionRepository;

    // 인터뷰 첫 질문 TTS 변환을 위해 호춯되는 파서드
    public Mono<WebSocketConnectResponse> issueWebSocketId(
            String email, String voice, Boolean restartFlag,
            restartInterviewRequest request
    )
            throws JsonProcessingException {

        // 1. 필요 데이터 DB 조회
        Member member = domainFindUtils.getMemberByEmail(email);

        String dbQuestion = null;
        if (member.getIsFirstFinished() && !restartFlag) {
            List<QuestionCandidate> questionCandidates = member.getQuestionCandidates();
            dbQuestion = questionCandidates.get(0).getQuestionText();
        } else if (restartFlag) {
            dbQuestion = interviewQuestionRepository.findTopByInterviewIdOrderBySequenceDesc(
                            request.getInterviewId())
                    .orElseThrow(() -> new GeneralException(ErrorStatus.QUESTION_NOT_FOUND))
                    .getQuestionText();
        }

        // TTS 변환을 위한 외부 AI 호출 메서드 호출 -> DB 저장 체인 연결
        return aiUtils.callFastApiForStartTts(AiStartTtsRequest.builder()
                        .dbQuestion(dbQuestion)
                        .nickname(member.getNickname())
                        .isFirstFinished(member.getIsFirstFinished())
                        .voice(voice)
                        .build())
                .flatMap(aiStartTtsResponse ->
                        Mono.fromCallable(() -> interviewService.saveInterviewAndQuestion(
                                member,
                                aiStartTtsResponse,
                                voice,
                                restartFlag,
                                restartFlag ? request.getInterviewId() : null
                        )))
                .subscribeOn(Schedulers.boundedElastic());
    }

    // DB 상태 변경 -> AI 파이프라인 호출 -> DB 저장 -> DB 상태 변경의 무거운 파이프라인을 분리하기 위한 메서드
    public FollowUpQuestionDTO processOneQnA(AudioMessageRequest request) throws IOException {

        // 클라이언트에서 보낸 Base64 데이터를 순수 바이트 배열로 변환
        byte[] audioBytes = Base64.getDecoder().decode(request.getAudioData());
        // 만약 오디오 파일이 비어있다면
        if (audioBytes == null || audioBytes.length == 0) {
            throw new GeneralException(ErrorStatus.EMPTY_AUDIO_FILE);
        }

        log.info("수신된 파일 크기: {} bytes", audioBytes.length);

        log.info("오디오 파일 저장 시작 및 DB 락 시도");
        // 1. 트랜잭션 O, 상태관리를 통한 DB 락 걸기 및 오디오 파일 저장
        AiPipelineReadyDto aiPipelineReadyDto = interviewService.lockQuestionAndSaveAudio(request,
                audioBytes);
        log.info("오디오 파일 저장 및 DB 락 완료");

        // todo: 첫 인터뷰에 한하여 시퀀스가 6 이상인 경우에 자동 종료 분기 필요
        // 2. 트랜잭션 X, AI 서버 호출
        AiPipelineResponse aiPipelineResponse = aiUtils.callFastApiForNextQuestion(audioBytes,
                aiPipelineReadyDto.getSequence(), aiPipelineReadyDto.getNickname(),
                aiPipelineReadyDto.getPreviousQuestion(),
                aiPipelineReadyDto.getIsFirstFinished(),
                aiPipelineReadyDto.getVoice());

        log.info("DB 락 해제 시도 및 응답 시도");
        // 3. 트랜잭션 O, AI 응답 -> DB에 저장 후 락 풀기 후 DTO로 조립하여 응답
        return interviewService.saveAiResultAndUnlock(request,
                aiPipelineResponse, aiPipelineReadyDto.getSavedFilePath());
    }

    /**
     * 1. redis를 조회하여 청크된 사용자 답변 텍스트 조립.
     * <p>
     * 2. AI 파이프라인 호출에 필요한 도메인 데이터 조회(트랜잭션을 짧게 가져감).
     * <p>
     * 3. AI 파이프라인호출(비교적 긴 시간 네트워크 I/O 발생, 트랜잭션 격리).
     * <p>
     * 4. 최종 결과 DB 저장 및 WebSocket 응답(트랜잭션).
     *
     * @return
     */


    public Mono<FollowUpQuestionDTO> generateNextQuestionFacade(Integer interviewId,
            Integer questionId, long start, String email) {

        log.info("Facade 레이어 도착");

        // 1. redis 키 접두사 조립
        String redisKey =
                "interviewId:" + interviewId + "questionId:" + questionId;

        AiPipelineReadyDto aiPipelineReadyDto = parseDtoFromJson(
                redisService.getValues(redisKey + "aiPipelineReadyDto"));

        // 2. 미리 필요한 데이터 redis 조회
        String savedPath = redisService.getValues(redisKey + "savedPath");
        String answerText = redisService.getOrderedChunks(redisKey + "chunks");

        log.info("AI 파이프라인 호출 시작");
        // questionId로 sequence 조회 후 분기
        return Mono.zip(
                        Mono.fromCallable(() -> domainFindUtils.getInterviewQuestionById(questionId))
                                .subscribeOn(Schedulers.boundedElastic()),
                        Mono.fromCallable(() -> domainFindUtils.getMemberByEmail(email))
                                .subscribeOn(Schedulers.boundedElastic())
                )
                .flatMap(tuple -> {
                    InterviewQuestion interviewQuestion = tuple.getT1();
                    Member member = tuple.getT2();
                    // 최초 사용자이며 6 이상이면 다음 질문이 없음 -> 종료 로직 호출
                    if (!member.getIsFirstFinished() && interviewQuestion.getSequence() + 1 >= 6) {
                        log.info("sequence 기준치 도달. 인터뷰 종료 파이프라인으로 전환");
                        // 즉시 DB에 저장 후 종료 파서드 호출
                        return exitInterviewFacade(email, InterviewExitRequest.builder()
                                .interviewId(interviewId)
                                .questionId(questionId)
                                .build())
                                .then(Mono.fromRunnable(() ->
                                        messagingTemplate.convertAndSend( // 프론트에 종료되었다고 응답
                                                "/topic/interview/tmp/" + interviewId,
                                                FollowUpQuestionDTO.builder()
                                                        .interviewId(interviewId)
                                                        .questionId(questionId)
                                                        .isExit(true) // 여기만 응답에 포함
                                                        .build()
                                        )));

                    }
                    // 3. AI 파이프라인 호출(여기서 비동기 호출 사용)
                    return Mono.fromCallable(() -> Files.readAllBytes(Paths.get(savedPath)))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(audioBytes ->
                                    aiUtils.callFastApiForNextQuestionV2(audioBytes,
                                            aiPipelineReadyDto.getSequence() + 1,
                                            aiPipelineReadyDto.getNickname(),
                                            aiPipelineReadyDto.getPreviousQuestion(),
                                            aiPipelineReadyDto.getIsFirstFinished(),
                                            answerText,
                                            aiPipelineReadyDto.getVoice()))
                            .flatMap(aiPipelineResponse -> {
                                // DB 저장, dto Redis 캐싱
                                return Mono.fromCallable(() -> {
                                    Integer nextQuestionId = interviewService.saveQuestionAndAnswer(
                                            interviewId,
                                            questionId, aiPipelineResponse.getQuestionText(),
                                            savedPath,
                                            answerText, aiPipelineResponse.getInterviewStateDb()
                                                    .getFusedScores());

                                    // redis에 저장하기 위한 데이터 준비
                                    String dto = objectMapper.writeValueAsString(
                                            AiPipelineReadyDto.builder()
                                                    .previousQuestion(
                                                            aiPipelineResponse.getQuestionText())
                                                    .isFirstFinished(
                                                            aiPipelineReadyDto.getIsFirstFinished())
                                                    .nickname(aiPipelineReadyDto.getNickname())
                                                    .sequence(aiPipelineReadyDto.getSequence() + 1)
                                                    .voice(aiPipelineReadyDto.getVoice())
                                                    .build());
                                    String newRedisKey =
                                            "interviewId:" + interviewId + "questionId:"
                                                    + nextQuestionId;
                                    redisService.setValues(newRedisKey + "aiPipelineReadyDto", dto,
                                            Duration.ofMinutes(5));
                                    // 저장 완료 lock 상태 해제
                                    redisService.deleteValues(
                                            "interviewId:" + interviewId + "lock");
                                    log.info("요청 및 반환 종료. 걸린 시간: {}",
                                            System.currentTimeMillis() - start);
                                    return new FollowUpQuestionDTO(aiPipelineResponse, interviewId,
                                            nextQuestionId);
                                }).subscribeOn(Schedulers.boundedElastic());
                            })
                            .doOnSuccess(followUpQuestionDTO -> {
                                aiUtils.callFastApiKeyword(KeywordRequest
                                                .builder()
                                                .answer(answerText)
                                                .question(interviewQuestion.getQuestionText())
                                                .build())
                                        .flatMap(keywordResponse ->
                                                Mono.fromRunnable(() ->
                                                                interviewService.saveKeyword(
                                                                        interviewQuestion,
                                                                        keywordResponse.getKeyword()))
                                                        .subscribeOn(Schedulers.boundedElastic())
                                        ).subscribe();

                            });
                });
    }

    // 키워드 생성 로직 호출
    private Mono keywordFacade(FollowUpQuestionDTO followUpQuestionDTO, String answerText) {

        // 1. 필요 도메인 객체 조회
        return Mono.fromCallable(() -> domainFindUtils.getInterviewQuestionById(
                        followUpQuestionDTO.getQuestionId())).subscribeOn(Schedulers.boundedElastic())
                .flatMap(
                        question -> (
                                aiUtils.callFastApiKeyword(KeywordRequest.builder()
                                        .answer(answerText)
                                        .question(question.getQuestionText())
                                        .build())
                        )
                );
    }

    // 역직렬화 헬퍼 메서드
    private AiPipelineReadyDto parseDtoFromJson(String json) {
        try {
            return objectMapper.readValue(json, AiPipelineReadyDto.class);
        } catch (JsonProcessingException e) {
            log.info("역직렬화 중 오류 발생");
            throw new GeneralException(ErrorStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // 인터뷰 종료 시 호출 될 파서드
    public Mono<Void> exitInterviewFacade(String email, InterviewExitRequest request) {

        // redis에 있을 마지막 답변 조회 및 저장 경로 조회
        String redisKey =
                "interviewId:" + request.getInterviewId() + "questionId:" + request.getQuestionId();
        String savedPath = redisService.getValues(redisKey + "savedPath");

        String answerText = redisService.getOrderedChunks(redisKey + "chunks");

        // 메서드 리턴 이후에도 람다에 캡처되어 체인 완료 시점까지 유지됨
        AtomicReference<InterviewQuestion> questionRef = new AtomicReference<>();
        AtomicReference<AiTitleResponse> aiTitleResponseRef = new AtomicReference<>();

        // 1. 디스크에서 음성 원본 파일을 받아옴
        return Mono.fromCallable(() -> Files.readAllBytes(Paths.get(savedPath)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(bytes -> Mono.fromCallable(() -> {
                            // 2. 마지막 답변을 저장함
                            InterviewQuestion question = interviewService.saveAnswer(
                                    request.getQuestionId(), answerText, savedPath);
                            questionRef.set(question);
                            return bytes;
                        }).subscribeOn(Schedulers.boundedElastic())
                )
                .flatMap(bytes ->
                        // 3. STT 정제 + 감정 분석 병렬 호출
                        Mono.zip(
                                aiUtils.lastAnswerStt(answerText),
                                aiUtils.lastAnswerEmotion(answerText, bytes)
                        )
                )
                .flatMap(tuple -> Mono.fromCallable(() -> {
                            // 4. 감정, 정제 텍스트 저장
                            interviewService.saveEmotionTts(
                                    tuple.getT1(),
                                    tuple.getT2(),
                                    request
                            );
                            // 5. DB에 마지막 답변 저장 및 필요 도메인 조회 및 다음 질문 생성을 위한 준비(질문 답변 쌍 조합 및 답변 없는 질문 삭제)
                            return interviewService.findDomainMakeQnACouple(email,
                                    request.getInterviewId());
                        }).subscribeOn(Schedulers.boundedElastic())
                )
                .flatMap(answerTts -> {

                    // 답변이 저장된 적 없다면 예외를 발생시킴
                    if (answerTts.getQnACouple() == null || answerTts.getQnACouple().isEmpty()) {
                        answerTts.protectEmptyString();
                    }

                    // 2. AI 시작 질문 생성 및 제목 생성 로직
                    Mono<AiTitleResponse> aiTitleResponseMono = aiUtils.callFastApiForInterviewTitleV2(
                            AiTitleRequest.builder()
                                    .interviewContext(answerTts.getQnACouple())
                                    .build());

                    Mono<AiNextOpeningResponse> aiNextOpeningResponseMono = aiUtils.callFastApiForNextOpeningV2(
                            AiNextOpeningRequest.builder()
                                    .interviewContext(answerTts.getQnACouple())
                                    .nickname(answerTts.getMember().getNickname())
                                    .build());

                    return Mono.zip(aiNextOpeningResponseMono, aiTitleResponseMono)
                            .flatMap(aiResponse -> Mono.fromRunnable(() -> {
                                        // 저장 로직
                                        AiNextOpeningResponse openingQuestionResponse = aiResponse.getT1();
                                        AiTitleResponse titleResponse = aiResponse.getT2();

                                        // rag에 보낼 메타데이터 조립
                                        for (Turn turn : answerTts.getTurns()) {
                                            turn.setMetadata(titleResponse.getTitle());
                                        }

                                        aiTitleResponseRef.set(titleResponse);
                                        interviewService.saveExitAiResponse(
                                                request.getInterviewId(),
                                                titleResponse.getTitle(),
                                                openingQuestionResponse.getDbQuestion());
                                        // 성공 시 isSaving 키 삭제
                                        redisService.deleteValues(
                                                "interviewId:" + request.getInterviewId() + "isSaving");
                                    }).subscribeOn(Schedulers.boundedElastic())
                            ).retry(3)
                            // 파이프라인 완료 후 RAG 적재
                            .doOnSuccess(ignored -> Mono.zip(
                                            aiUtils.callFastApiForRagUpsert(UpsertRequest.builder()
                                                            .userId(String.valueOf(answerTts.getMember().getId()))
                                                            .interviewId(String.valueOf(request.getInterviewId()))
                                                            .turns(answerTts.getTurns())
                                                            .metadata(Map.of("title",
                                                                    aiTitleResponseRef.get().getTitle()))
                                                            .build())
                                                    .doOnError(e -> log.error("RAG upsert 실패: {}",
                                                            e.getMessage())),
                                            aiUtils.callFastApiKeyword(KeywordRequest.builder()
                                                    .question(questionRef.get().getQuestionText())
                                                    .answer(answerText)
                                                    .build())
                                    ).doOnSuccess(tuple -> interviewService.saveKeyword(questionRef.get(),
                                            tuple.getT2().getKeyword()))
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .subscribe(
                                            null,
                                            error -> log.error("키워드 저장 파이프라인 실패: {}",
                                                    error.getMessage())
                                    ));  // 별도 subscribe로 체인과 분리
                })
                .then(); // Mono<Void> 반영
    }

    // 사용자가 아무런 녹음도 하지 않고 종료했을 경우 실행 될 파서드
    public void exitInterviewNoAudioFacade(TotalChunkRequest request, String email,
            String redisKey) {

        // 1. 해당 대화의 모든 질문, 답변 쌍 조회
        String context = interviewService.makeAnswerQuestionCouple(request.getInterviewId());

        Member member = domainFindUtils.getMemberByEmail(email);

        // 2. 다음 첫 질문 및 제목 생성
        Mono.zip(
                aiUtils.callFastApiForNextOpeningV2(AiNextOpeningRequest.builder()
                        .interviewContext(context)
                        .nickname(member.getNickname())
                        .build()),

                aiUtils.callFastApiForInterviewTitleV2(AiTitleRequest
                        .builder()
                        .interviewContext(context)
                        .build())
        ).flatMap(tuple -> {
            // 첫 질문 및 제목 생성된 값을 받음
            AiNextOpeningResponse aiNextOpeningResponse = tuple.getT1();
            AiTitleResponse aiTitleResponse = tuple.getT2();

            // DB에 저장, 인터뷰 상태 전환, redis 키 삭제
            return Mono.fromRunnable(
                    () -> {
                        interviewService.saveExitAiResponse(request.getInterviewId(),
                                aiTitleResponse.getTitle(),
                                aiNextOpeningResponse.getDbQuestion());
                        redisService.deleteValues(redisKey + "lock");
                        redisService.deleteValues(redisKey + "isSaving");
                    }).subscribeOn(Schedulers.boundedElastic());
        }).subscribe(
                null,
                error -> log.error("종료 파이프라인 실패: {}", error.getMessage())
        );

    }

    // 인터뷰가 삭제되고, rag에서도 관련 값 삭제를 위해 호출되는 facade
    public void deleteInterviewFacade(String email, Integer interviewId) {

        // 1. 필요 도메인 엔티티 조회
        Member member = domainFindUtils.getMemberByEmail(email);

        // 2. DB에서 제거
        interviewService.deleteInterview(interviewId, member.getId());

        // 3. rag 제거 유틸 호출
        aiUtils.callFastApiForRagDelete(DeleteRequest.builder()
                        .interviewId(String.valueOf(interviewId))
                        .userId(String.valueOf(member.getId()))
                        .build())
                .subscribe();
    }

    public Mono<Void> forceFinishInterviewFacade(String email, Integer interviewId) {
        log.info("인터뷰 강제 종료 파이프라인 시작 - interviewId: {}", interviewId);

        // 데이터 조회 및 질문 정리
        // findDomainMakeQnACouple 메서드에서 내부적으로 답변이 null인 질문을 delete
        return Mono.fromCallable(() -> interviewService.findDomainMakeQnACouple(email, interviewId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(answerTts -> {

                    // 만약 대화 내역이 아예 없는 경우 방어 로직
                    if (answerTts.getQnACouple() == null || answerTts.getQnACouple().isEmpty()) {
                        answerTts.protectEmptyString();
                    }

                    // AI 제목 생성 및 다음 오프닝 생성 비동기 호출
                    Mono<AiTitleResponse> aiTitleResponseMono = aiUtils.callFastApiForInterviewTitleV2(
                            AiTitleRequest.builder()
                                    .interviewContext(answerTts.getQnACouple())
                                    .build());

                    Mono<AiNextOpeningResponse> aiNextOpeningResponseMono = aiUtils.callFastApiForNextOpeningV2(
                            AiNextOpeningRequest.builder()
                                    .interviewContext(answerTts.getQnACouple())
                                    .nickname(answerTts.getMember().getNickname())
                                    .build());

                    return Mono.zip(aiNextOpeningResponseMono, aiTitleResponseMono)
                            .flatMap(aiResponse -> Mono.fromRunnable(() -> {
                                        // 상태 업데이트 및 DB 저장
                                        AiNextOpeningResponse openingQuestionResponse = aiResponse.getT1();
                                        AiTitleResponse titleResponse = aiResponse.getT2();

                                        interviewService.saveExitAiResponse(
                                                interviewId,
                                                titleResponse.getTitle(),
                                                openingQuestionResponse.getDbQuestion());

                                        log.info("인터뷰 강제 종료 완료 및 상태 변경 - Title: {}",
                                                titleResponse.getTitle());
                                    }).subscribeOn(Schedulers.boundedElastic())
                            )
                            .retry(2) // 네트워크 불안정 대비 재시도

                            // RAG 적재
                            .doOnSuccess(ignored -> {
                                aiUtils.callFastApiForRagUpsert(UpsertRequest.builder()
                                                .userId(String.valueOf(answerTts.getMember().getId()))
                                                .interviewId(String.valueOf(interviewId))
                                                .turns(answerTts.getTurns()) // 정리된 대화 내역만 적재
                                                .metadata(Map.of("title", "인터뷰(중단)"))
                                                .build())
                                        .doOnError(e -> log.error("강제 종료 인터뷰 RAG 적재 실패: {}",
                                                e.getMessage()))
                                        .subscribe(); // 비동기로 별도 실행
                            });
                })
                .then();
    }
}
