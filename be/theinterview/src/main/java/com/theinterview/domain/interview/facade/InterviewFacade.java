package com.theinterview.domain.interview.facade;

import com.theinterview.domain.interview.dto.FastApiRequest.AiPipelineReadyDto;
import com.theinterview.domain.interview.dto.FastApiResponse.AiPipelineResponse;
import com.theinterview.domain.interview.dto.InterviewRequest.AudioMessageRequest;
import com.theinterview.domain.interview.dto.InterviewResponse.FollowUpQuestionDTO;
import com.theinterview.domain.interview.service.AiUtils;
import com.theinterview.domain.interview.service.InterviewService;
import com.theinterview.global.config.redis.RedisService;
import com.theinterview.global.response.exception.GeneralException;
import com.theinterview.global.response.status.ErrorStatus;
import com.theinterview.global.util.DomainFindUtils;
import java.io.IOException;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InterviewFacade {

    private final InterviewService interviewService;
    private final AiUtils aiUtils;
    private final RedisService redisService;
    private final DomainFindUtils domainFindUtils;

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
    public String generateNextQuestionFacade(Integer interviewId, Integer questionId) {

        log.info("Facade 레이어 도착");

        // 1. redis 키 접두사 조립
        String redisKey =
                "interviewId:" + interviewId + "questionId:" + questionId;

        // 2. 미리 필요한 데이터 redis 조회
        String savedPath = redisService.getValues(redisKey + "savedPath");
        String answerText = redisService.getOrderedChunks(redisKey + "chunks");

        // 3. 서비스 레이어를 호출하여 필요 도메인 데이터를 가져옴(짧은 트랜잭션)
        AiPipelineReadyDto aiPipelineReadyDto = interviewService.getReadyDto(
                interviewId,
                questionId);

        log.info("AI 파이프라인 호출 시작");
        // 3. AI 파이프라인 호출(여기서 비동기 호출 사용)
        aiUtils.callFastApiForNextQuestionV2()
        // 아직 AI 파이프라인 만들어지지 않음

        // 4. DB 저장 로직(트랜잭션) -> DB에 저장하는 로직 자체는 어렵지 않음 subscribe 체인 안에서 해결하면 될 듯
        interviewService.saveQuestionAndAnswer(interviewId, questionId,
                "임시 꼬리질문 텍스트", savedPath, answerText);

        // 최종 응답 -> 다음 질문 전송
        return "임시 꼬리질문 텍스트";
    }
}
