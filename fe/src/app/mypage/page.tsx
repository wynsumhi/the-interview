// src/app/mypage/page.tsx
"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { MOCK_USER, MOCK_ARCHIVE_LIST } from "@/constants/mockData";
import { logout } from "@/services/auth";
import styles from "./page.module.css";

export default function MyPage() {
  const [activeTab, setActiveTab] = useState<"profile" | "bookmarks">(
    "profile",
  );
  const user = MOCK_USER;

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
              value={user.email}
              disabled
              className={styles.disabledInput}
            />
          </div>
          <div className={styles.inputGroup}>
            <label>닉네임</label>
            <input
              type="text"
              defaultValue={user.nickname}
              className={styles.input}
            />
          </div>
          <div className={styles.inputGroup}>
            <label>비밀번호 변경</label>
            <input
              type="password"
              placeholder="새 비밀번호"
              className={styles.input}
            />
            <input
              type="password"
              placeholder="비밀번호 확인"
              className={styles.input}
            />
          </div>
          <div className={styles.btnGroup}>
            <button className={styles.saveBtn}>변경사항 저장</button>
            <button className={styles.withdrawBtn}>회원탈퇴</button>
          </div>
        </section>
      ) : (
        /* 2. 북마크 목록 섹션 (조회 및 삭제) */
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
