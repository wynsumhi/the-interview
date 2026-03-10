// src/app/signup/page.tsx
"use client";

import Link from "next/link";
import styles from "./page.module.css";

export default function SignupPage() {
  return (
    <main className={styles.container}>
      <div className={styles.authBox}>
        <Link href="/" className={styles.logo}>
          방구석 인터뷰
        </Link>

        <h2 className={styles.title}>Create Account</h2>

        <form className={styles.form}>
          <div className={styles.inputGroup}>
            <label>이메일</label>
            <input
              type="email"
              placeholder="example@email.com"
              className={styles.input}
            />
          </div>

          <div className={styles.inputGroup}>
            <label>비밀번호</label>
            <input
              type="password"
              placeholder="8자 이상 입력해주세요"
              className={styles.input}
            />
            <span className={styles.validation}>
              * 영문, 숫자, 특수문자 조합 8자 이상
            </span>
          </div>

          <div className={styles.inputGroup}>
            <label>비밀번호 확인</label>
            <input
              type="password"
              placeholder="비밀번호를 한번 더 입력해주세요"
              className={styles.input}
            />
          </div>

          <div className={styles.inputGroup}>
            <label>닉네임 (별칭)</label>
            <input
              type="text"
              placeholder="나를 표현할 이름을 정해주세요"
              className={styles.input}
            />
          </div>

          <button type="submit" className={styles.submitBtn}>
            회원가입 하기
          </button>
        </form>

        <p className={styles.switchAuth}>
          이미 계정이 있으신가요? <Link href="/login">로그인</Link>
        </p>
      </div>
    </main>
  );
}
