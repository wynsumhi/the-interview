import { create } from "zustand";

interface NotificationState {
  count: number;
  setCount: (count: number) => void;
  decrement: () => void;
}

export const useNotificationStore = create<NotificationState>((set) => ({
  count: 0,
  setCount: (count) => set({ count }),
  decrement: () => set((state) => ({ count: Math.max(0, state.count - 1) })),
}));
