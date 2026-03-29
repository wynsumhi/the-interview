import instance from "@/lib/axios";
import type { ApiResponse } from "@/types/interview";

export interface NotificationItem {
  notificationId: number;
  interviewId: number;
  senderNickname: string;
  message: string;
  shareLink: string;
  createdAt: string;
}

// GET /api/v1/notification - 내 알림 목록 조회
export async function fetchNotifications(): Promise<NotificationItem[]> {
  const res = await instance.get<ApiResponse<{ notifications: NotificationItem[] }>>(
    "/notification",
  );
  return res.data.result.notifications;
}
