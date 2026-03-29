// src/app/mypage/page.tsx
"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { logout } from "@/services/auth";
import { getMe, updateMe, deleteMe } from "@/services/member";
import {
  fetchNotifications,
  deleteNotification,
  type NotificationItem,
} from "@/services/notification";
import styles from "./page.module.css";

export default function MyPage() {
  const router = useRouter();
  const [activeTab, setActiveTab] = useState<"notifications" | "profile">(
    "notifications",
  );
  const [email, setEmail] = useState("");
  const [nickname, setNickname] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);

  const handleDeleteNotification = async (notificationId: number) => {
    try {
      await deleteNotification(notificationId);
      setNotifications((prev) =>
        prev.filter((item) => item.notificationId !== notificationId),
      );
    } catch {
      // 삭제 실패 시 무시
    }
  };

  useEffect(() => {
    getMe().then((data) => {
      setEmail(data.email);
      setNickname(data.nickname);
    });
    fetchNotifications()
      .then(setNotifications)
      .catch(() => {});
  }, []);

  // 회원 정보 변경 저장 처리 함수
  // 닉네임 유효성 검사: 3~8자의 영문 또는 한글만 허용
  const handleSave = async () => {
    const nicknameRegex = /^[a-zA-Z가-힣]{3,8}$/;
    if (!nicknameRegex.test(nickname)) {
      alert("닉네임은 3~8자의 영문 또는 한글만 가능합니다.");
      return;
    }
    if (password !== confirmPassword) {
      alert("비밀번호와 비밀번호 확인이 일치하지 않습니다.");
      return;
    }
    try {
      await updateMe({ nickname, ...(password ? { password } : {}) });
      alert("회원 정보 변경이 완료되었습니다.");
      router.push("/archive");
    } catch (error: unknown) {
      const message =
        (error as { response?: { data?: { message?: string } } })?.response
          ?.data?.message ??
        "변경사항 저장에 실패했습니다. 다시 시도해 주세요.";
      alert(message);
    }
  };

  // 회원 탈퇴 처리 함수
  const handleWithdraw = async () => {
    if (confirm("정말로 회원탈퇴 하시겠습니까?")) {
      try {
        // 회원 탈퇴 API 호출 후 로그아웃 처리
        await deleteMe();
        await logout();
        // 토큰 제거
        localStorage.removeItem("accessToken");
        localStorage.removeItem("refreshToken");
        document.cookie = "accessToken=; path=/; max-age=0";
        router.push("/");
      } catch (error: unknown) {
        const message =
          (error as { response?: { data?: { message?: string } } })?.response
            ?.data?.message ?? "회원탈퇴에 실패했습니다. 다시 시도해 주세요.";
        alert(message);
      }
    }
  };

  return (
    <main className={styles.container}>
      <header className={styles.header}>
        <h1 className={styles.title}>MY PAGE</h1>
        <p className={styles.subtitle}>
          확인 가능 알람 및 계정 설정을 관리합니다.
        </p>
      </header>

      {/* 탭 내비게이션 */}
      <nav className={styles.tabNav}>
        <button
          className={`${styles.tabItem} ${activeTab === "notifications" ? styles.active : ""}`}
          onClick={() => setActiveTab("notifications")}
        >
          활동 알림
        </button>
        <button
          className={`${styles.tabItem} ${activeTab === "profile" ? styles.active : ""}`}
          onClick={() => setActiveTab("profile")}
        >
          내 정보 수정
        </button>
      </nav>

      {activeTab === "notifications" ? (
        /* 1. 알림 섹션 */
        <section className={styles.notificationList}>
          {notifications.length === 0 ? (
            <p className={styles.emptyState}>새로운 알림이 없습니다.</p>
          ) : (
            notifications.map((n) => (
              <div key={n.notificationId} className={styles.notificationItem}>
                <button
                  className={styles.notificationBody}
                  onClick={() => router.push(`/magazine/${n.shareLink}`)}
                >
                  <span className={styles.notificationMsg}>{n.message}</span>
                  <span className={styles.notificationDate}>
                    {new Date(n.createdAt).toLocaleDateString("ko-KR", {
                      year: "numeric",
                      month: "2-digit",
                      day: "2-digit",
                    })}
                  </span>
                </button>
                <button
                  className={styles.notificationDeleteBtn}
                  onClick={() => handleDeleteNotification(n.notificationId)}
                  aria-label="알림 삭제"
                >
                  ✕
                </button>
              </div>
            ))
          )}
        </section>
      ) : (
        /* 2. 내 정보 수정 섹션 (이메일 확인, 닉네임/비번 변경, 탈퇴) */
        <section className={styles.section}>
          <div className={styles.inputGroup}>
            <label>이메일 (아이디)</label>
            <input
              type="text"
              value={email}
              disabled
              className={styles.disabledInput}
            />
          </div>
          <div className={styles.inputGroup}>
            <label>닉네임</label>
            <input
              type="text"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              className={styles.input}
            />
          </div>
          <div className={styles.inputGroup}>
            <label>비밀번호 변경</label>
            <input
              type="password"
              placeholder="새 비밀번호"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className={styles.input}
            />
            <input
              type="password"
              placeholder="비밀번호 확인"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className={styles.input}
            />
          </div>
          <div className={styles.btnGroup}>
            <button className={styles.withdrawBtn} onClick={handleWithdraw}>
              회원탈퇴
            </button>
            <button className={styles.saveBtn} onClick={handleSave}>
              변경사항 저장
            </button>
          </div>
        </section>
      )}
    </main>
  );
}
