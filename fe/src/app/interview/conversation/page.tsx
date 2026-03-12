"use client";

import { useState, useEffect, useRef } from "react";
import styles from "./page.module.css";
import { useInterviewState } from "@/hooks/useInterviewState";

// 파형 바 갯수
const BAR_COUNT = 12;

// 기본 파형 높이
const DEFAULT_HEIGHTS = Array<number>(BAR_COUNT).fill(4);

export default function InterviewSessionPage() {
  // 인터뷰 상태 머신 hook 사용
  const { state, setState } = useInterviewState();

  // 질문 타이핑 표시용 state
  const [displayTitle, setDisplayTitle] = useState("");

  // 음성 파형 높이
  const [waveHeights, setWaveHeights] = useState<number[]>(DEFAULT_HEIGHTS);

  // 마이크 에러 메시지
  const [micError, setMicError] = useState<string | null>(null);

  // 질문 목업 데이터
  const fullTitle =
    "오늘 당신의 마음을 가장 크게 흔들었던 문장은 무엇이었나요?";

  // WebAudio 관련 ref
  const audioContextRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const streamRef = useRef<MediaStream | null>(null);

  // MediaRecorder 인스턴스
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);

  // 녹음 데이터 저장
  const audioChunksRef = useRef<Blob[]>([]);

  // 침묵 감지 타이머
  const silenceTimerRef = useRef<NodeJS.Timeout | null>(null);

  // requestAnimationFrame 관리
  const animationFrameRef = useRef<number | null>(null);

  // state를 ref로 보관 (requestAnimationFrame 내부에서 최신 state 접근)
  const stateRef = useRef(state);

  useEffect(() => {
    stateRef.current = state;
  }, [state]);

  // 1. 질문 타이핑 애니메이션
  useEffect(() => {
    let interval: NodeJS.Timeout;

    if (state === "AI_SPEAKING") {
      let index = 0;

      interval = setInterval(() => {
        setDisplayTitle(fullTitle.substring(0, index));
        index++;

        // 질문 타이핑 완료(→ 사용자 녹음 상태 전환)
        if (index > fullTitle.length) {
          clearInterval(interval);

          setTimeout(() => {
            setState("USER_RECORDING");
          }, 1500);
        }
      }, 70);
    }

    return () => {
      if (interval) clearInterval(interval);
    };
  }, [state]);

  // 2. 음성 감지 시작 / 종료
  useEffect(() => {
    if (state === "USER_RECORDING") {
      startListening();
    } else {
      stopListening();
    }

    return () => stopListening();
  }, [state]);

  // 마이크 시작
  const startListening = async () => {
    try {
      if (!navigator.mediaDevices) {
        setMicError("마이크는 HTTPS 환경에서만 사용할 수 있습니다.");
        return;
      }

      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      streamRef.current = stream;

      setMicError(null);

      // MediaRecorder 생성
      const mediaRecorder = new MediaRecorder(stream);

      mediaRecorderRef.current = mediaRecorder;

      audioChunksRef.current = [];

      mediaRecorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          audioChunksRef.current.push(event.data);
        }
      };

      mediaRecorder.start();

      const AudioContextClass =
        window.AudioContext || (window as any).webkitAudioContext;

      const audioContext = new AudioContextClass();

      const analyser = audioContext.createAnalyser();

      const source = audioContext.createMediaStreamSource(stream);

      // 음성 분석 설정
      analyser.fftSize = 256;
      analyser.smoothingTimeConstant = 0.8;

      source.connect(analyser);

      audioContextRef.current = audioContext;
      analyserRef.current = analyser;

      analyzeVolume();
    } catch (err) {
      console.error("마이크 접근 실패:", err);

      setMicError("마이크 접근에 실패했습니다. HTTPS 환경인지 확인해 주세요.");
    }
  };

  // 음성 볼륨 분석
  const analyzeVolume = () => {
    if (!analyserRef.current) return;

    const dataArray = new Uint8Array(analyserRef.current.frequencyBinCount);

    const checkUpdate = () => {
      if (stateRef.current !== "USER_RECORDING") return;

      if (!analyserRef.current) return;

      analyserRef.current.getByteFrequencyData(dataArray);

      // 파형 막대 계산
      const step = Math.floor(dataArray.length / BAR_COUNT);

      const heights = Array.from({ length: BAR_COUNT }, (_, i) => {
        const slice = dataArray.slice(i * step, (i + 1) * step);

        const avg = slice.reduce((a, b) => a + b, 0) / slice.length;

        return Math.max(4, Math.round((avg / 255) * 80));
      });

      setWaveHeights(heights);

      // 평균 볼륨 계산
      const avg = dataArray.reduce((p, c) => p + c, 0) / dataArray.length;

      // 침묵 감지
      if (avg < 10) {
        if (!silenceTimerRef.current) {
          silenceTimerRef.current = setTimeout(() => {
            console.log("침묵 감지 → AI 분석 단계");

            setState("WAITING_AI");
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

  // 마이크 종료
  const stopListening = () => {
    if (animationFrameRef.current) {
      cancelAnimationFrame(animationFrameRef.current);
      animationFrameRef.current = null;
    }

    if (silenceTimerRef.current) {
      clearTimeout(silenceTimerRef.current);
      silenceTimerRef.current = null;
    }

    streamRef.current?.getTracks().forEach((track) => track.stop());

    audioContextRef.current?.close();

    setWaveHeights(DEFAULT_HEIGHTS);
  };

  return (
    <main
      className={`${styles.container} ${
        state === "USER_RECORDING" ? styles.focusMode : ""
      }`}
    >
      <div className={styles.orbWrapper}>
        <div className={`${styles.orb} ${styles.orb1}`} />
        <div className={`${styles.orb} ${styles.orb2}`} />
      </div>

      <section className={styles.content}>
        <div className={styles.questionBox}>
          <h2 className={styles.questionText}>
            {displayTitle}

            {state === "AI_SPEAKING" && (
              <span className={styles.cursor}>|</span>
            )}
          </h2>
        </div>

        <div className={styles.visualizerArea}>
          {/* 사용자 녹음 UI */}

          <div
            className={styles.listeningUI}
            style={{ opacity: state === "USER_RECORDING" ? 1 : 0 }}
          >
            {micError ? (
              <p className={styles.errorText}>{micError}</p>
            ) : (
              <div className={styles.waveContainer}>
                {waveHeights.map((h, i) => (
                  <div
                    key={i}
                    className={styles.waveBar}
                    style={{ height: `${h}px` }}
                  />
                ))}
              </div>
            )}

            <p className={styles.statusText}>당신의 목소리에 집중하고 있어요</p>
          </div>

          {/* AI 분석 UI */}

          {state === "WAITING_AI" && (
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
