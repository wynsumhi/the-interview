import axios from "axios";
import instance from "@/lib/axios";
import type { ApiResponse, InterviewDetailItem, InterviewHistoryPage, ExplorePage } from "@/types/interview";

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
  // true이면 현재 질문이 온보딩 인터뷰의 마지막 질문 — 자동 종료 및 NEXT 버튼 숨김에 사용
  isFirstFinished?: boolean;
  // true이면 온보딩 5문항이 모두 완료됨 — 인터뷰 종료 신호
  isExit?: boolean;
}

// 인터뷰 생성 + 첫 질문 생성
// LLM 질문 생성 + TTS 변환이 포함되어 기본 10초 타임아웃을 초과할 수 있으므로 60초로 설정
// signal: React StrictMode 이중 실행 등으로 컴포넌트가 정리될 때 요청을 중단하기 위한 AbortSignal
export async function createInterview(
  isPrevAffected: boolean,
  signal?: AbortSignal,
): Promise<CreateInterviewResult> {
  const res = await instance.post("/interview", { isPrevAffected }, { timeout: 60000, signal });
  return res.data.result;
}

// GET /api/v1/interview/explore?page=N - 공개 인터뷰 탐색 목록 조회
export async function fetchExploreInterviews(page: number): Promise<ExplorePage> {
  const res = await instance.get<ApiResponse<ExplorePage>>("/interview/explore", {
    params: { page },
  });
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

// DELETE /api/v1/interview/{interviewId} - 인터뷰 삭제
// 400: 잘못된 요청 (이미 삭제됐거나 소유권 없음)
export async function deleteInterview(interviewId: number): Promise<void> {
  try {
    await instance.delete(`/interview/${interviewId}`);
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 400) {
      throw new Error("BAD_REQUEST");
    }
    throw error;
  }
}

// POST /api/v1/interview/exitV2 - 인터뷰 종료 버튼
// 전체 답변 오디오(answerAudio)와 청크 수(totalChunk)를 함께 전송해 감정 분석 및 요약 처리
export async function exitInterviewV2(payload: {
  interviewId: number;
  questionId: number;
  answerAudio: string; // Base64 인코딩된 전체 답변 오디오
  totalChunk: string;
}): Promise<void> {
  await instance.post("/interview/exitV2", payload, { timeout: 60000 });
}
