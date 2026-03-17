package com.theinterview.domain.interview.facade;

import com.theinterview.domain.interview.dto.FastApiResponse.AiPipelineResponse;
import com.theinterview.domain.interview.dto.InterviewRequest.AudioMessageRequest;
import com.theinterview.domain.interview.dto.InterviewResponse.FollowUpQuestionDTO;
import com.theinterview.domain.interview.service.AiUtils;
import com.theinterview.domain.interview.service.InterviewService;
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

    // DB 상태 변경 -> AI 파이프라인 호출 -> DB 저장 -> DB 상태 변경의 무거운 파이프라인을 분리하기 위한 메서드
    public FollowUpQuestionDTO processOneQnA(AudioMessageRequest request) throws IOException {

        // 클라이언트에서 보낸 Base64 데이터를 순수 바이트 배열로 변환
        byte[] audioBytes = Base64.getDecoder().decode(request.getAudioData());
        log.info("수신된 파일 크기: {} bytes", audioBytes.length);

        log.info("오디오 파일 저장 시작 및 DB 락 시도");
        // 1. 트랜잭션 O, 상태관리를 통한 DB 락 걸기 및 오디오 파일 저장
        String savedFilePath = interviewService.lockQuestionAndSaveAudio(request, audioBytes);
        log.info("오디오 파일 저장 및 DB 락 완료");

        // 2. 트랜잭션 X, AI 서버 호출
        AiPipelineResponse aiPipelineResponse = aiUtils.callFastApiForNextQuestion(audioBytes);

        log.info("DB 락 해제 시도 및 응답 시도");
        // 3. 트랜잭션 O, AI 응답 -> DB에 저장 후 락 풀기 후 DTO로 조립하여 응답
        return interviewService.saveAiResultAndUnlock(request,
                aiPipelineResponse, savedFilePath);
    }
}
