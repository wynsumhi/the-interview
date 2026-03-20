package com.theinterview.domain.interview.dto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

public abstract class InterviewRequest {

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class AudioMessageRequest {

        private Integer interviewId;  // 웹소켓 방 번호 == interviewId
        private Integer questionId; // 답변한 질문 id
        private String audioData; // 음성 data(base64)
        private boolean isFinal;  // 마지막 질문 여부
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class InterviewStartRequest {

        private boolean isPrevAffected;
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class InterviewExitRequest {

        private Integer interviewId; // 웹소켓 방 번호 구독을 위해 클라이언트 측에서 가지고 있는 interviewId
        private Integer questionId; // 이전에 받은 questionId
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class AudioChunkRequest {

        private Integer interviewId; // 어떤 인터뷰인지(인터뷰 pk)
        private Integer questionId; // 사용자가 어떤 질문에 대해 답변했는지
        private Integer chuckIndex; // 청킹된 오디오의 순서 보장을 위한 순번
        private byte[] audioData; // Base64로 인코딩된 오디오 조각 배열
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class TotalChunkRequest {

        private Integer interviewId; // 어떤 인터뷰인지(인터뷰 pk)
        private Integer questionId; // 사용자가 어떤 질문에 대해 답변했는지
        private String totalChunk;  // 총 청크 수
        private byte[] answerAudio; // 사용자의 답변 음성 원본
    }
}
