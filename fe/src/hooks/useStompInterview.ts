import { useRef, useCallback } from "react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import type { FollowUpQuestionDTO } from "@/services/interview";

// 실시간 STT 청크 전송 규격 - /app/interview/audio/chunk
// chunkIndex: 0-based 순번으로 서버 측 순서 보장 및 누락 감지에 사용
interface ChunkMessageRequest {
  interviewId: number;
  questionId: number;
  audioData: string; // Base64 인코딩된 1-3초 오디오 조각
  chunkIndex: number;
}

// 최종 오디오 전송 규격 - /app/interview/audio/next 또는 /exitV2
// totalChunk: 서버 동기화 오류 방지를 위해 String 타입으로 전송
interface FinalMessageRequest {
  interviewId: number;
  questionId: number;
  audioData: string; // Base64 인코딩된 전체 응답 오디오 (감정 분석용)
  totalChunk: string;
}

export function useStompInterview() {
  const clientRef = useRef<Client | null>(null);

  // STOMP 연결 + 채널 구독
  const connect = useCallback(
    (
      apiUrl: string,
      interviewId: number,
      token: string,
      onMessage: (dto: FollowUpQuestionDTO) => void,
      onConnect?: () => void
    ) => {
      // 최초 연결인지 재연결인지 구분하기 위한 플래그
      // onConnect는 reconnect 시에도 재실행되므로, 최초 1회만 콜백을 호출해야 함
      let isFirstConnect = true;

      const client = new Client({
        // SockJS로 핸드셰이크 후 STOMP 세션 수립
        webSocketFactory: () =>
          new SockJS(`${apiUrl}/api/ws-connect`) as WebSocket,
        // HTTP 헤더 대신 STOMP CONNECT 프레임에 JWT 삽입
        connectHeaders: {
          Authorization: `Bearer ${token}`,
          interviewId: String(interviewId),
        },
        onConnect: () => {
          // 재연결 시에도 구독을 다시 등록해야 메시지를 수신할 수 있음
          client.subscribe(
            `/topic/interview/tmp/${interviewId}`,
            (message) => {
              const dto: FollowUpQuestionDTO = JSON.parse(message.body);
              onMessage(dto);
            }
          );
          // 최초 연결 시에만 onConnect 콜백 실행
          // 재연결 시 실행하면 setStatus("AI_SPEAKING")가 재호출되어 인터뷰 흐름이 초기화됨
          if (isFirstConnect) {
            isFirstConnect = false;
            onConnect?.();
          }
        },
        onStompError: (frame) => {
          console.error("STOMP error", frame);
        },
      });

      client.activate();
      clientRef.current = client;
    },
    []
  );

  // 1-3초 단위 실시간 STT 청크 전송 (/app/interview/audio/chunk)
  const sendChunk = useCallback((payload: ChunkMessageRequest) => {
    clientRef.current?.publish({
      destination: "/app/interview/audio/chunk",
      body: JSON.stringify(payload),
    });
  }, []);

  // 전체 응답 오디오 전송 - 꼬리질문 생성 트리거 (/app/interview/audio/next)
  const sendNext = useCallback((payload: FinalMessageRequest) => {
    clientRef.current?.publish({
      destination: "/app/interview/audio/next",
      body: JSON.stringify(payload),
    });
  }, []);

  // 인터뷰 종료 시 전체 오디오 전송 - 감정 분석용 (/app/interview/audio/exitV2)
  const sendExit = useCallback((payload: FinalMessageRequest) => {
    clientRef.current?.publish({
      destination: "/app/interview/audio/exitV2",
      body: JSON.stringify(payload),
    });
  }, []);

  // 연결 해제
  const disconnect = useCallback(() => {
    clientRef.current?.deactivate();
    clientRef.current = null;
  }, []);

  return { connect, sendChunk, sendNext, sendExit, disconnect };
}
