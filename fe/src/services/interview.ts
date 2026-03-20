import axios from "axios";
import instance from "@/lib/axios";
import type { ApiResponse, InterviewDetailItem, InterviewHistoryPage } from "@/types/interview";

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
// LLM 질문 생성 + TTS 변환이 포함되어 기본 10초 타임아웃을 초과할 수 있으므로 60초로 설정
export async function createInterview(
  isPrevAffected: boolean,
): Promise<CreateInterviewResult> {
  const res = await instance.post("/interview", { isPrevAffected }, { timeout: 60000 });
  return res.data.result;
}

// GET /api/v1/interview?page=N - 인터뷰 히스토리 목록 조회
// page는 0-based (서버 기준)
export async function fetchInterviewHistory(
  page: number,
): Promise<InterviewHistoryPage> {
  const res = await instance.get<ApiResponse<InterviewHistoryPage>>("/interview", {
    params: { page },
  });
  return res.data.result;
}

// GET /api/v1/interview/{interviewId} - 인터뷰 상세 조회
// 403: 권한 없음 / 404: 존재하지 않는 인터뷰
export async function fetchInterviewDetail(
  interviewId: number,
): Promise<InterviewDetailItem> {
  try {
    const res = await instance.get<ApiResponse<InterviewDetailItem>>(
      `/interview/${interviewId}`,
    );
    return res.data.result;
  } catch (error) {
    if (axios.isAxiosError(error)) {
      const status = error.response?.status;
      if (status === 403) throw new Error("FORBIDDEN");
      if (status === 404) throw new Error("NOT_FOUND");
    }
    throw error;
  }
}

// POST /api/v1/interview/exit - 인터뷰 강제 종료
// 서버가 현재까지의 대화를 바탕으로 AI 요약 제목을 생성해 반환
export async function exitInterview(
  interviewId: number,
  questionId: number,
): Promise<string> {
  const res = await instance.post("/interview/exit", { interviewId, questionId }, { timeout: 60000 });
  // 응답 구조: { result: "AI가 생성한 인터뷰 제목" }
  return res.data.result;
}
