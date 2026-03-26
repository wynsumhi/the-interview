import { create } from "zustand";
import { persist } from "zustand/middleware";

interface BookmarkState {
  /** interviewAnswerId → 북마크 여부 (localStorage에 영구 저장) */
  bookmarks: Record<number, boolean>;
  setBookmark: (interviewAnswerId: number, value: boolean) => void;
}

export const useBookmarkStore = create<BookmarkState>()(
  persist(
    (set) => ({
      bookmarks: {},
      setBookmark: (interviewAnswerId, value) =>
        set((state) => ({
          bookmarks: { ...state.bookmarks, [interviewAnswerId]: value },
        })),
    }),
    { name: "bookmark-storage" },
  ),
);
