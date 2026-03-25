// src/store/useUserStore.ts - 사용자의 로그인 상태(UUID)를 저장
import { create } from "zustand";
import { persist } from "zustand/middleware";

interface UserState {
  userUuid: string | null;
  nickname: string | null;
  isLoggedIn: boolean;
  // 로그인 성공 시 호출할 함수
  setLoginInfo: (userUuid: string, nickname: string) => void;
  // 로그아웃 시 호출할 함수
  clearLoginInfo: () => void;
}

export const useUserStore = create<UserState>()(
  persist(
    (set) => ({
      userUuid: null,
      nickname: null,
      isLoggedIn: false,
      setLoginInfo: (userUuid, nickname) =>
        set({ userUuid, nickname, isLoggedIn: true }),
      clearLoginInfo: () =>
        set({ userUuid: null, nickname: null, isLoggedIn: false }),
    }),
    {
      name: "user-storage", // localStorage에 저장될 키 이름
    },
  ),
);
