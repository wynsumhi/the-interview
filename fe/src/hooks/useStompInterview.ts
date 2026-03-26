import { useRef, useCallback } from "react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import type { FollowUpQuestionDTO } from "@/services/interview";

interface AudioMessageRequest {
  interviewId: number;
  questionId: number;
  audioData: string;
  isFinal: boolean;
}

export function useStompInterview() {
  const clientRef = useRef<Client | null>(null);

  const connect = useCallback(
    (
      apiUrl: string,
      interviewId: number,
      token: string,
      onMessage: (dto: FollowUpQuestionDTO) => void,
      onConnect?: () => void,
    ) => {
      let isFirstConnect = true;

      const client = new Client({
        webSocketFactory: () =>
          new SockJS(`${apiUrl}/api/ws-connect`) as WebSocket,
        connectHeaders: {
          Authorization: `Bearer ${token}`,
          interviewId: String(interviewId),
        },
        onConnect: () => {
          const handleIncomingMessage = (message: { body: string }) => {
            const dto: FollowUpQuestionDTO = JSON.parse(message.body);
            onMessage(dto);
          };

          client.subscribe(
            `/topic/interview/tmp/${interviewId}`,
            handleIncomingMessage,
          );

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
    [],
  );

  const sendAudio = useCallback((payload: AudioMessageRequest) => {
    clientRef.current?.publish({
      destination: "/app/interview/audio",
      body: JSON.stringify(payload),
    });
  }, []);

  const disconnect = useCallback(() => {
    clientRef.current?.deactivate();
    clientRef.current = null;
  }, []);

  return { connect, sendAudio, disconnect };
}
