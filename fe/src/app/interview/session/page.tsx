// src/app/interview/session/page.tsx
"use client";

import { useState, useEffect, useRef } from "react";
import styles from "./page.module.css";

type InterviewStatus = "AI_SPEAKING" | "USER_LISTENING" | "ANALYZING";

const BAR_COUNT = 12;
const DEFAULT_HEIGHTS = Array<number>(BAR_COUNT).fill(4);

export default function InterviewSessionPage() {
  const [status, setStatus] = useState<InterviewStatus>("AI_SPEAKING");
  const [displayTitle, setDisplayTitle] = useState("");
  const [waveHeights, setWaveHeights] = useState<number[]>(DEFAULT_HEIGHTS);

  const fullTitle =
    "오늘 당신의 마음을 가장 크게 흔들었던 문장은 무엇이었나요?";
  const audioContextRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const silenceTimerRef = useRef<NodeJS.Timeout | null>(null);
  const animationFrameRef = useRef<number | null>(null);
  const statusRef = useRef<InterviewStatus>(status);

  useEffect(() => {
    statusRef.current = status;
  }, [status]);

  // 1. 타이핑 및 초기 상태 제어
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

  // 2. VAD 로직: 목소리 감지 시작
  useEffect(() => {
    if (status === "USER_LISTENING") {
      startListening();
    } else {
      stopListening();
    }
    return () => stopListening();
  }, [status]);

  const startListening = async () => {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      streamRef.current = stream;

      const AudioContextClass =
        window.AudioContext || (window as any).webkitAudioContext;
      const audioContext = new AudioContextClass();
      const analyser = audioContext.createAnalyser();
      const source = audioContext.createMediaStreamSource(stream);

      // fftSize 256으로 높여 주파수 해상도 향상 (frequencyBinCount = 128)
      analyser.fftSize = 256;
      analyser.smoothingTimeConstant = 0.8; // 막대 움직임 부드럽게
      source.connect(analyser);

      audioContextRef.current = audioContext;
      analyserRef.current = analyser;

      analyzeVolume();
    } catch (err) {
      console.error("마이크 접근 실패:", err);
    }
  };

  const analyzeVolume = () => {
    if (!analyserRef.current) return;

    const dataArray = new Uint8Array(analyserRef.current.frequencyBinCount);

    const checkUpdate = () => {
      if (statusRef.current !== "USER_LISTENING") return;
      if (!analyserRef.current) return;

      analyserRef.current.getByteFrequencyData(dataArray);

      // 주파수 데이터를 BAR_COUNT개 막대에 균등 매핑 (4px ~ 80px)
      const step = Math.floor(dataArray.length / BAR_COUNT);
      const heights = Array.from({ length: BAR_COUNT }, (_, i) => {
        const slice = dataArray.slice(i * step, (i + 1) * step);
        const avg = slice.reduce((a, b) => a + b, 0) / slice.length;
        return Math.max(4, Math.round((avg / 255) * 80));
      });
      setWaveHeights(heights);

      // 평균 볼륨으로 침묵 감지
      const avg = dataArray.reduce((p, c) => p + c, 0) / dataArray.length;
      if (avg < 10) {
        if (!silenceTimerRef.current) {
          silenceTimerRef.current = setTimeout(() => {
            console.log("침묵 감지됨: 분석 단계로 전환");
            setStatus("ANALYZING");
          }, 3000);
        }
      } else {
        if (silenceTimerRef.current) {
          clearTimeout(silenceTimerRef.current);
          silenceTimerRef.current = null;
        }
      }

      animationFrameRef.current = requestAnimationFrame(checkUpdate);
    };

    checkUpdate();
  };

  const stopListening = () => {
    if (animationFrameRef.current) {
      cancelAnimationFrame(animationFrameRef.current);
      animationFrameRef.current = null;
    }
    if (silenceTimerRef.current) clearTimeout(silenceTimerRef.current);
    silenceTimerRef.current = null;
    streamRef.current?.getTracks().forEach((track) => track.stop());
    audioContextRef.current?.close();
    setWaveHeights(DEFAULT_HEIGHTS);
  };

  return (
    <main
      className={`${styles.container} ${status === "USER_LISTENING" ? styles.focusMode : ""}`}
    >
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
          <div
            className={styles.listeningUI}
            style={{ opacity: status === "USER_LISTENING" ? 1 : 0 }}
          >
            <div className={styles.waveContainer}>
              {waveHeights.map((h, i) => (
                <div
                  key={i}
                  className={styles.waveBar}
                  style={{ height: `${h}px` }}
                />
              ))}
            </div>
            <p className={styles.statusText}>당신의 목소리에 집중하고 있어요</p>
          </div>

          {status === "ANALYZING" && (
            <div className={styles.analyzingUI}>
              <div className={styles.loadingBar} />
              <p className={styles.statusText}>
                문장을 아카이빙하고 있습니다...
              </p>
            </div>
          )}
        </div>
      </section>
    </main>
  );
}
