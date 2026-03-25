import { create } from "zustand";
import type { ShareBoardInfo } from "@/types/board";

interface BoardState {
  boardInfo: ShareBoardInfo | null;
  // 보드 페이지 진입 시 API 응답을 저장
  setBoardInfo: (info: ShareBoardInfo) => void;
  // 보드 페이지를 벗어날 때 초기화
  clearBoardInfo: () => void;
}

export const useBoardStore = create<BoardState>((set) => ({
  boardInfo: null,
  setBoardInfo: (info) => set({ boardInfo: info }),
  clearBoardInfo: () => set({ boardInfo: null }),
}));
