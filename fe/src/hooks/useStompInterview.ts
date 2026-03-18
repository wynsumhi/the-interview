import { useRef, useCallback } from "react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import type { FollowUpQuestionDTO } from "@/services/interview";

// WebSocket으로 전송하는 음성 데이터 규격
interface AudioMessageRequest {
  interviewId: number;
  questionId: number;
  audioData: string; // Base64 인코딩
  isFinal: boolean;
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
            `/topic/interview/room/${interviewId}`,
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

  // 녹음된 음성을 STOMP로 전송
  const sendAudio = useCallback((payload: AudioMessageRequest) => {
    clientRef.current?.publish({
      destination: "/app/interview/audio",
      body: JSON.stringify(payload),
    });
  }, []);

  // 연결 해제
  const disconnect = useCallback(() => {
    clientRef.current?.deactivate();
    clientRef.current = null;
  }, []);

  return { connect, sendAudio, disconnect };
}
