"use client";

import styles from "./page.module.css";
import { useInterviewState } from "@/hooks/useInterviewState";

export default function ConversationPage() {
  const {
    state,
    startThinking,
    startSpeaking,
    startRecording,
    startUploading,
    waitAI,
    finishInterview,
  } = useInterviewState();

  return (
    <main className={styles.container}>
      <h2>현재 상태: {state}</h2>

      <div className={styles.controls}>
        <button onClick={startThinking}>AI 질문 생성</button>
        <button onClick={startSpeaking}>AI 질문 말하기</button>
        <button onClick={startRecording}>사용자 녹음</button>
        <button onClick={startUploading}>음성 업로드</button>
        <button onClick={waitAI}>AI 분석</button>
        <button onClick={finishInterview}>인터뷰 종료</button>
      </div>
    </main>
  );
}
