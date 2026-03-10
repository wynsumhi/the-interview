// src/app/login/page.tsx
"use client";

import Link from "next/link";
import styles from "./page.module.css";

export default function LoginPage() {
  return (
    <main className={styles.container}>
      <div className={styles.authBox}>
        {/* 로고 (메인페이지 바로가기) */}
        <Link href="/" className={styles.logo}>
          방구석 인터뷰
        </Link>

        <h2 className={styles.title}>Welcome Back</h2>

        <form className={styles.form}>
          <div className={styles.inputGroup}>
            <label>이메일</label>
            <input
              type="email"
              placeholder="email@example.com"
              className={styles.input}
            />
          </div>
          <div className={styles.inputGroup}>
            <label>비밀번호</label>
            <input
              type="password"
              placeholder="••••••••"
              className={styles.input}
            />
          </div>
          <button type="submit" className={styles.submitBtn}>
            로그인
          </button>
        </form>

        <div className={styles.divider}>
          <span>또는</span>
        </div>

        {/* 소셜 로그인 영역 */}
        <div className={styles.socialGroup}>
          <button className={`${styles.socialBtn} ${styles.kakao}`}>
            카카오 로그인
          </button>
          <button className={`${styles.socialBtn} ${styles.naver}`}>
            네이버 로그인
          </button>
          <button className={`${styles.socialBtn} ${styles.google}`}>
            구글 로그인
          </button>
        </div>

        <p className={styles.switchAuth}>
          아직 계정이 없으신가요? <Link href="/signup">회원가입</Link>
        </p>
      </div>
    </main>
  );
}
