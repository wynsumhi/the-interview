// 인터뷰 상태 관리를 위한 Zustand 스토어
import { create } from "zustand";

// 인터뷰 상태 인터페이스 구조 정의
interface InterviewState {
  questionIndex: number;
  transcript: string;
  isRecording: boolean;
  setTranscript: (text: string) => void;
  startRecording: () => void;
  stopRecording: () => void;
  nextQuestion: () => void;
}

// Zustand 스토어 생성 및 초기 상태 설정
export const useInterviewStore = create<InterviewState>((set) => ({
  questionIndex: 0, // 현재 질문 인덱스(첫 질문은 0부터 시작)
  transcript: "", // 현재 질문에 대한 답변 텍스트
  isRecording: false, // 녹음 상태 (true: 녹음 중, false: 녹음 중지) - false로 녹음 시작 전 상태 초기화
  setTranscript: (text) => set({ transcript: text }), // transcript(답변 텍스트) 업데이트 함수
  startRecording: () => set({ isRecording: true }), // 녹음 시작 함수
  stopRecording: () => set({ isRecording: false }), // 녹음 중지 함수
  nextQuestion: () =>
    set((state) => ({
      questionIndex: state.questionIndex + 1,
      transcript: "",
    })),
}));
