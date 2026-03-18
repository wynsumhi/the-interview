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
          // 내 방 채널 구독 - AI 꼬리질문을 여기서 수신
          client.subscribe(
            `/topic/interview/room/${interviewId}`,
            (message) => {
              const dto: FollowUpQuestionDTO = JSON.parse(message.body);
              onMessage(dto);
            }
          );
          onConnect?.();
        },
        onStompError: (frame) => {
          console.error("STOMP error", frame);
        },
        // 자동 재연결 비활성화: 재연결 시 onConnect가 재실행되면
        // 중복 구독 생성 + setStatus("AI_SPEAKING") 재호출로 소켓이 새로 연결되는 것처럼 보이는 문제 방지
        reconnectDelay: 0,
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
