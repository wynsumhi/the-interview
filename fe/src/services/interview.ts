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
