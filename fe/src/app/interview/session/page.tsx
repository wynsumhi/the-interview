"use client";

import { useState, useEffect } from "react";
import styles from "./page.module.css";

type InterviewStatus = "AI_SPEAKING" | "USER_LISTENING" | "ANALYZING";

export default function InterviewSessionPage() {
  const [status, setStatus] = useState<InterviewStatus>("AI_SPEAKING");
  const [displayTitle, setDisplayTitle] = useState("");
  const fullTitle =
    "오늘 당신의 마음을 가장 크게 흔들었던 문장은 무엇이었나요?";

  useEffect(() => {
    let interval: NodeJS.Timeout;

    if (status === "AI_SPEAKING") {
      let index = 0;
      interval = setInterval(() => {
        setDisplayTitle(fullTitle.substring(0, index));
        index++;

        if (index > fullTitle.length) {
          clearInterval(interval);
          setTimeout(() => setStatus("USER_LISTENING"), 1500);
        }
      }, 70);
    }
    return () => {
      if (interval) clearInterval(interval);
    };
  }, [status]);

  return (
    <main
      className={`${styles.container} ${status === "USER_LISTENING" ? styles.focusMode : ""}`}
    >
      {/* 1. 다크 모드 전용 배경 오로라 (여러 층으로 구성) */}
      <div className={styles.orbWrapper}>
        <div className={`${styles.orb} ${styles.orb1}`} />
        <div className={`${styles.orb} ${styles.orb2}`} />
      </div>

      <section className={styles.content}>
        <div className={styles.questionBox}>
          <h2 className={styles.questionText}>
            {displayTitle}
            {status === "AI_SPEAKING" && (
              <span className={styles.cursor}>|</span>
            )}
          </h2>
        </div>

        <div className={styles.visualizerArea}>
          {status === "USER_LISTENING" && (
            <div className={styles.listeningUI}>
              {/* 2. 다크 모드 전용 다이나믹 웨이브 애니메이션 */}
              <div className={styles.waveContainer}>
                {[...Array(12)].map((_, i) => (
                  <div key={i} className={styles.waveBar} />
                ))}
              </div>
              <p className={styles.statusText}>
                당신의 목소리에 집중하고 있어요
              </p>
            </div>
          )}
        </div>
      </section>
    </main>
  );
}
