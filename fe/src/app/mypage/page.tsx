// src/app/mypage/page.tsx
"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { MOCK_ARCHIVE_LIST } from "@/constants/mockData";
import { logout } from "@/services/auth";
import { getMe, updateMe, deleteMe } from "@/services/member";
import styles from "./page.module.css";

export default function MyPage() {
  const router = useRouter();
  const [activeTab, setActiveTab] = useState<"profile" | "bookmarks">(
    "profile",
  );
  const [email, setEmail] = useState("");
  const [nickname, setNickname] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  useEffect(() => {
    getMe().then((data) => {
      setEmail(data.email);
      setNickname(data.nickname);
    });
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
      await updateMe({ nickname, password });
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
          계정 설정 및 보관된 대화를 관리합니다.
        </p>
      </header>

      {/* 탭 내비게이션 */}
      <nav className={styles.tabNav}>
        <button
          className={`${styles.tabItem} ${activeTab === "profile" ? styles.active : ""}`}
          onClick={() => setActiveTab("profile")}
        >
          내 정보 수정
        </button>
        <button
          className={`${styles.tabItem} ${activeTab === "bookmarks" ? styles.active : ""}`}
          onClick={() => setActiveTab("bookmarks")}
        >
          북마크 보관함
        </button>
      </nav>

      {activeTab === "profile" ? (
        /* 1. 내 정보 수정 섹션 (이메일 확인, 닉네임/비번 변경, 탈퇴) */
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
      ) : (
        /* 2. 북마크 목록 섹션 (조회 및 삭제) */
        // TODO: 실제 API 연동 후 MOCK_ARCHIVE_LIST 제거
        <section className={styles.bookmarkList}>
          {MOCK_ARCHIVE_LIST.length > 0 ? (
            MOCK_ARCHIVE_LIST.map((item) => (
              <div key={item.interview_id} className={styles.bookmarkCard}>
                <div className={styles.bInfo}>
                  <span className={styles.bDate}>
                    {new Date(item.created_at).toLocaleDateString()}
                  </span>
                  <h3 className={styles.bTitle}>{item.title}</h3>
                </div>
                <div className={styles.cardActions}>
                  <button className={styles.viewBtn}>조회</button>
                  <button className={styles.deleteBtn}>삭제</button>
                </div>
              </div>
            ))
          ) : (
            <div className={styles.emptyState}>
              <p>북마크된 인터뷰가 없습니다.</p>
            </div>
          )}
        </section>
      )}
    </main>
  );
}
