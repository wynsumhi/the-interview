"use client";

import QuestionCard from "@/src/components/interview/QuestionCard";
import RecordingButton from "@/src/components/interview/RecordingButton";
import TranscriptBox from "@/src/components/interview/TranscriptBox";
import Timer from "@/src/components/interview/Timer";

export default function InterviewSession() {
  // TEST
  const question = "당신의 어린 시절을 가장 잘 설명하는 기억은 무엇인가요?";

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

      <QuestionCard question={question} />

      <RecordingButton />

      <TranscriptBox />
    </main>
  );
}
