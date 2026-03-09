"use client";

export default function InterviewSession() {
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

      <div>여기 질문 넣기</div>
      <button>Start Recording</button>
      <div>transcript 음성 → 텍스트 변환 영역</div>
    </main>
  );
}
