import { create } from "zustand";
import type { MyBookmarkItem } from "@/types/board";

interface BookmarkState {
  /** interviewAnswerId(= sourceId) → 북마크 여부 */
  bookmarks: Record<number, boolean>;
  /** /share-board/me 응답으로 전체 북마크 상태를 초기화 */
  initBookmarks: (items: MyBookmarkItem[]) => void;
  /** 단일 항목의 북마크 상태를 업데이트 (낙관적 업데이트 / 롤백에 사용) */
  setBookmark: (interviewAnswerId: number, value: boolean) => void;
}

export const useBookmarkStore = create<BookmarkState>((set) => ({
  bookmarks: {},
  initBookmarks: (items) => {
    const map: Record<number, boolean> = {};
    items.forEach(({ sourceId }) => {
      map[sourceId] = true;
    });
    set({ bookmarks: map });
  },
  setBookmark: (interviewAnswerId, value) =>
    set((state) => ({
      bookmarks: { ...state.bookmarks, [interviewAnswerId]: value },
    })),
}));
