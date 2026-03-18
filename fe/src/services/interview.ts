import instance from "@/lib/axios";

// POST /api/v1/interview 응답 타입
export interface CreateInterviewResult {
  interviewId: number;
  questionId: number;
  interviewTitle: string; // 첫 질문 텍스트
  ttsAudioBase64: string;
}

// WebSocket 구독 채널로 받는 꼬리질문 DTO
export interface FollowUpQuestionDTO {
  interviewId: number;
  questionId: number;
  sttResult: string;
  followUpQuestion: string;
  ttsAudioBase64: string;
}

// 인터뷰 생성 + 첫 질문 생성
export async function createInterview(
  isPrevAffected: boolean,
): Promise<CreateInterviewResult> {
  const res = await instance.post("/interview", { isPrevAffected });
  return res.data.result;
}

// POST /api/v1/interview/exit - 인터뷰 강제 종료
// 서버가 현재까지의 대화를 바탕으로 AI 요약 제목을 생성해 반환
export async function exitInterview(
  interviewId: number,
  questionId: number,
): Promise<string> {
  const res = await instance.post("/interview/exit", { interviewId, questionId });
  // 응답 구조: { result: "AI가 생성한 인터뷰 제목" }
  return res.data.result;
}
