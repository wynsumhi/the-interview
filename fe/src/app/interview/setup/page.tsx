// src/app/interview/start/page.tsx
"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import styles from "./page.module.css";

export default function InterviewStartPage() {
  const router = useRouter();
  const [duration, setDuration] = useState<number>(10); // 기본값 10분

  const timeOptions = [5, 10, 15, 20, 25, 30];

  const handleStart = () => {
    // 선택된 시간(duration)만 쿼리로 전달
    router.push(`/interview/session?duration=${duration}`);
  };

  return (
    <main className={styles.container}>
      <header className={styles.header}>
        <h1 className={styles.title}>SETTING</h1>
        <p className={styles.subtitle}>
          오늘의 대화는 어느 정도의 깊이로 나누어볼까요?
        </p>
      </header>

      <section className={styles.formSection}>
        {/* 시간 설정 선택 영역 */}
        <div className={styles.timeSelectionWrapper}>
          <label className={styles.label}>Select Duration</label>
          <div className={styles.timeGrid}>
            {timeOptions.map((time) => (
              <button
                key={time}
                type="button"
                className={`${styles.timeChip} ${duration === time ? styles.active : ""}`}
                onClick={() => setDuration(time)}
              >
                <span className={styles.timeValue}>{time}</span>
                <span className={styles.timeUnit}>MINUTES</span>
              </button>
            ))}
          </div>
        </div>

        <div className={styles.guideBox}>
          <div className={styles.guideItem}>
            <span className={styles.icon}>🎤</span>
            <p>마이크 권한을 허용하면 자동으로 대화가 시작됩니다.</p>
          </div>
          <div className={styles.guideItem}>
            <span className={styles.icon}>🌙</span>
            <p>대화 중에는 오직 당신의 목소리에만 집중합니다.</p>
          </div>
        </div>

        <button className={styles.startBtn} onClick={handleStart}>
          START INTERVIEW
        </button>
      </section>
    </main>
  );
}
