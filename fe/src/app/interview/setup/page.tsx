"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import styles from "./page.module.css";

type InterviewVoice = "Robert" | "Sam" | "Sarah" | "Jessica";

export default function InterviewSetupPage() {
  const router = useRouter();

  const [voice, setVoice] = useState<InterviewVoice>("Jessica");
  const [playingVoice, setPlayingVoice] = useState<InterviewVoice | null>(null);
  const [checkingMic, setCheckingMic] = useState(false);
  const previewAudioRef = useRef<HTMLAudioElement | null>(null);

  const voiceOptions: InterviewVoice[] = ["Robert", "Sam", "Sarah", "Jessica"];

  useEffect(() => {
    return () => {
      previewAudioRef.current?.pause();
      previewAudioRef.current = null;
    };
  }, []);

  const handlePreview = (selectedVoice: InterviewVoice) => {
    if (playingVoice === selectedVoice) {
      previewAudioRef.current?.pause();
      previewAudioRef.current = null;
      setPlayingVoice(null);
      return;
    }

    previewAudioRef.current?.pause();

    const audio = new Audio(
      `/voice-samples/${selectedVoice.toLowerCase()}.wav`,
    );
    audio.onended = () => {
      if (previewAudioRef.current === audio) {
        previewAudioRef.current = null;
        setPlayingVoice(null);
      }
    };

    previewAudioRef.current = audio;
    setPlayingVoice(selectedVoice);
    audio.play().catch(() => {
      previewAudioRef.current = null;
      setPlayingVoice(null);
      alert("샘플 음성을 재생할 수 없습니다.");
    });
  };

  const handleStart = async () => {
    try {
      setCheckingMic(true);

      await navigator.mediaDevices.getUserMedia({ audio: true });

      const params = new URLSearchParams({ voice });

      router.push(`/interview/conversation?${params}`);
    } catch {
      alert("마이크 권한이 필요합니다.");
      setCheckingMic(false);
    }
  };

  return (
    <main className={styles.container}>
      <h1 className={styles.title}>SETTING</h1>

      <section className={styles.section}>
        <p className={styles.label}>Voice Type</p>

        <div className={styles.voiceGrid}>
          {voiceOptions.map((option) => (
            <div
              key={option}
              className={`${styles.voiceCard} ${
                voice === option ? styles.active : ""
              }`}
              role="button"
              tabIndex={0}
              onClick={() => setVoice(option)}
              onKeyDown={(event) => {
                if (event.key === "Enter" || event.key === " ") {
                  event.preventDefault();
                  setVoice(option);
                }
              }}
            >
              <div className={styles.voiceHeader}>
                <span className={styles.voiceName}>{option}</span>
                <div className={styles.voiceActions}>
                  <button
                    type="button"
                    className={styles.selectBtn}
                    onClick={(event) => {
                      event.stopPropagation();
                      setVoice(option);
                    }}
                  >
                    {voice === option ? "Selected" : "Select"}
                  </button>
                  <button
                    type="button"
                    className={styles.previewBtn}
                    aria-label={
                      playingVoice === option
                        ? `${option} sample stop`
                        : `${option} sample preview`
                    }
                    onClick={(event) => {
                      event.stopPropagation();
                      handlePreview(option);
                    }}
                  >
                    {playingVoice === option ? (
                      <svg
                        viewBox="0 0 24 24"
                        className={styles.previewIcon}
                        aria-hidden="true"
                      >
                        <rect x="6" y="6" width="12" height="12" rx="2" />
                      </svg>
                    ) : (
                      <svg
                        viewBox="0 0 24 24"
                        className={styles.previewIcon}
                        aria-hidden="true"
                      >
                        <path d="M11 5 6 9H3v6h3l5 4V5Z" />
                        <path d="M15.5 8.5a5 5 0 0 1 0 7" />
                        <path d="M18 6a8.5 8.5 0 0 1 0 12" />
                      </svg>
                    )}
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
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
