"use client";

import QuestionCard from "@/src/components/interview/QuestionCard";
import RecordingButton from "@/src/components/interview/RecordingButton";
import TranscriptBox from "@/src/components/interview/TranscriptBox";
import Timer from "@/src/components/interview/Timer";

import { useInterviewStore } from "@/src/store/interviewStore";

// TEST QUESTION - 실제 인터뷰에서는 API로 받아올 예정
const questions = [
  "당신의 어린 시절을 가장 잘 설명하는 기억은 무엇인가요?",
  "최근 당신이 가장 많이 생각하는 고민은 무엇인가요?",
  "당신에게 행복이란 어떤 의미인가요?",
];

export default function InterviewSession() {
  const {
    questionIndex,
    transcript,
    isRecording,
    startRecording,
    stopRecording,
    nextQuestion,
  } = useInterviewStore();

  const question = questions[questionIndex];

  return (
    <main
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        paddingTop: "2rem",
        gap: "1rem",
      }}
    >
      <h1>Start Interview</h1>

      <Timer />

      <QuestionCard question={questions[questionIndex]} />

      <RecordingButton />

      <TranscriptBox />
    </main>
  );
}
