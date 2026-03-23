package com.theinterview.domain.interview.facade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theinterview.domain.interview.dto.FastApiRequest.AiNextOpeningRequest;
import com.theinterview.domain.interview.dto.FastApiRequest.AiPipelineReadyDto;
import com.theinterview.domain.interview.dto.FastApiRequest.AiTitleRequest;
import com.theinterview.domain.interview.dto.FastApiResponse.AiNextOpeningResponse;
import com.theinterview.domain.interview.dto.FastApiResponse.AiPipelineResponse;
import com.theinterview.domain.interview.dto.FastApiResponse.AiTitleResponse;
import com.theinterview.domain.interview.dto.InterviewRequest.AudioMessageRequest;
import com.theinterview.domain.interview.dto.InterviewRequest.InterviewExitRequest;
import com.theinterview.domain.interview.dto.InterviewResponse.FollowUpQuestionDTO;
import com.theinterview.domain.interview.entity.InterviewQuestion;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
                aiPipelineReadyDto.getPreviousQuestion(), aiPipelineReadyDto.getIsFirstFinished());

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
                    if (!member.getIsFirstFinished() && interviewQuestion.getSequence() >= 6) {
                        log.info("sequence 기준치 도달. 인터뷰 종료 파이프라인으로 전환");
                        return exitInterviewFacade(email, InterviewExitRequest.builder()
                                .interviewId(interviewId)
                                .questionId(questionId)
                                .build())
                                .then(Mono.empty());
                    }
                    // 3. AI 파이프라인 호출(여기서 비동기 호출 사용)
                    return Mono.fromCallable(() -> Files.readAllBytes(Paths.get(savedPath)))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(audioBytes ->
                                    aiUtils.callFastApiForNextQuestionV2(audioBytes,
                                            aiPipelineReadyDto.getSequence(),
                                            aiPipelineReadyDto.getNickname(),
                                            aiPipelineReadyDto.getPreviousQuestion(),
                                            aiPipelineReadyDto.getIsFirstFinished(),
                                            answerText))
                            .flatMap(aiPipelineResponse -> {
                                // DB 저장, dto Redis 캐싱
                                return Mono.fromCallable(() -> {
                                    Integer nextQuestionId = interviewService.saveQuestionAndAnswer(
                                            interviewId,
                                            questionId, aiPipelineResponse.getQuestionText(),
                                            savedPath,
                                            answerText);

                                    // redis에 저장하기 위한 데이터 준비
                                    String dto = objectMapper.writeValueAsString(
                                            AiPipelineReadyDto.builder()
                                                    .previousQuestion(
                                                            aiPipelineResponse.getQuestionText())
                                                    .isFirstFinished(
                                                            aiPipelineReadyDto.getIsFirstFinished())
                                                    .nickname(aiPipelineReadyDto.getNickname())
                                                    .sequence(aiPipelineReadyDto.getSequence() + 1)
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
                            });
                });
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

    // 인터뷰 종료 시 호출되는 facade
    public Mono<Void> exitInterviewFacade(String email, InterviewExitRequest request) {

        // 1. 필요 도메인 조회 및 다음 질문 생성을 위한 준비(질문 답변 쌍 조합 및 답변 없는 질문 삭제)
        return Mono.fromCallable(() ->
                        interviewService.findDomainMakeQnACouple(email, request.getInterviewId()))
                .subscribeOn(Schedulers.boundedElastic())
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
                                        interviewService.saveExitAiResponse(
                                                request.getInterviewId(),
                                                titleResponse.getTitle(),
                                                openingQuestionResponse.getDbQuestion());
                                        // 성공 시 isSaving 키 삭제
                                        redisService.deleteValues(
                                                "interviewId:" + request.getInterviewId() + "isSaving");
                                    }).subscribeOn(Schedulers.boundedElastic())
                            ).retry(3);
                })
                .then(); // Mono<Void> 반영
    }
}
