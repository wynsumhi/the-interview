"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import styles from "./page.module.css";

type InterviewMode = "AUTO" | "CATEGORY";

export default function InterviewSetupPage() {
  const router = useRouter();

  const [duration, setDuration] = useState(10);
  const [mode, setMode] = useState<InterviewMode>("AUTO");
  const [category, setCategory] = useState("");
  const [checkingMic, setCheckingMic] = useState(false);

  const timeOptions = [5, 10, 15, 20, 25, 30];

  const handleStart = async () => {
    try {
      setCheckingMic(true);

      await navigator.mediaDevices.getUserMedia({ audio: true });

      const params = new URLSearchParams({
        duration: duration.toString(),
        mode,
        category,
      });

      router.push(`/interview/conversation?${params}`);
    } catch {
      alert("마이크 권한이 필요합니다.");
      setCheckingMic(false);
    }
  };

  return (
    <main className={styles.container}>
      <h1 className={styles.title}>SETTING</h1>

      {/* Duration */}
      <section className={styles.section}>
        <p className={styles.label}>Duration</p>

        <div className={styles.timeGrid}>
          {timeOptions.map((time) => (
            <button
              key={time}
              className={`${styles.timeChip} ${
                duration === time ? styles.active : ""
              }`}
              onClick={() => setDuration(time)}
            >
              {time}m
            </button>
          ))}
        </div>
      </section>

      {/* Mode */}
      <section className={styles.section}>
        <p className={styles.label}>Interview Type</p>

        <div className={styles.modeGrid}>
          <button
            className={`${styles.modeCard} ${
              mode === "AUTO" ? styles.active : ""
            }`}
            onClick={() => setMode("AUTO")}
          >
            <h3>Random</h3>
            <p>이전 대화 기반 질문으로 생성됩니다.</p>
          </button>

          <button
            className={`${styles.modeCard} ${
              mode === "CATEGORY" ? styles.active : ""
            }`}
            onClick={() => setMode("CATEGORY")}
          >
            <h3>Category</h3>
            <p>주제 기반 질문이 생성됩니다.</p>
          </button>
        </div>

        {mode === "CATEGORY" && (
          <input
            className={styles.categoryInput}
            placeholder="예: 사랑, 성장, 인간관계"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          />
        )}
      </section>

      <button className={styles.startBtn} onClick={handleStart}>
        START INTERVIEW
      </button>

      {checkingMic && (
        <div className={styles.overlay}>
          <div className={styles.loader} />
          <p>마이크 권한을 확인하고 있어요</p>
        </div>
      )}
    </main>
  );
}
