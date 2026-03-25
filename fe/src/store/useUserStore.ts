// src/store/useUserStore.ts - 사용자의 로그인 상태(UUID)를 저장
import { create } from "zustand";
import { persist } from "zustand/middleware";

interface UserState {
  uuid: string | null;
  nickname: string | null;
  isLoggedIn: boolean;
  // 로그인 성공 시 호출할 함수
  setLoginInfo: (uuid: string, nickname: string) => void;
  // 로그아웃 시 호출할 함수
  clearLoginInfo: () => void;
}

export const useUserStore = create<UserState>()(
  persist(
    (set) => ({
      uuid: null,
      nickname: null,
      isLoggedIn: false,
      setLoginInfo: (uuid, nickname) =>
        set({ uuid, nickname, isLoggedIn: true }),
      clearLoginInfo: () =>
        set({ uuid: null, nickname: null, isLoggedIn: false }),
    }),
    {
      name: "user-storage", // localStorage에 저장될 키 이름
    },
  ),
);
