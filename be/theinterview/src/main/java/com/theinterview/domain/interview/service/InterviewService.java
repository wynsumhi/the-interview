package com.theinterview.domain.interview.service;

import com.theinterview.domain.interview.Interview;
import com.theinterview.domain.interview.Repository.InterviewRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewService {

    private final MemberRepository memberRepository;
    private final InterviewRepository interviewRepository;

    @Transactional
    public FollowUpQuestionDTO oneQnAInterviewFlow(AudioMessageRequest request) throws IOException {

        // 클라이언트에서 보낸 Base64 데이터를 순수 바이트 배열로 변환
        byte[] audioBytes = Base64.getDecoder().decode(request.getAudioData());
        log.info("수신된 파일 크기: {} bytes", audioBytes.length);

        // MVP: 오디오 파일 프로젝트 최상단에 저장
        File dest = new File("test_audio_" + request.getRoomId() + ".webm");
        Files.write(dest.toPath(), audioBytes);
        log.info("음성 파일 저장 완료: {}", dest.getAbsoluteFile());
        String roomId = request.getRoomId();

        // todo: 여기서 fastapi 서버와 통신하고 STT, 꼬리질문, TTS 결과를 수신하며 DB에 필요한 데이터를 저장함

        // 일단 통신 테스트를 위해 임시 데이터 반호나
        return FollowUpQuestionDTO.builder()
                .roomId(roomId)
                .sttResult("테스트 텍스트입니다.")
                .followUpQuestion("테스트 꼬리질문 텍스트입니다.")
                .ttsAudioBase64(request.getAudioData()) // 임시 원보 데이터 반환
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
