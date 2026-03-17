"use client";

import { useState, useEffect, useRef, useCallback, Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { useStompInterview } from "@/hooks/useStompInterview";
import { createInterview } from "@/services/interview";
import type { FollowUpQuestionDTO } from "@/services/interview";
import styles from "./page.module.css";

// 인터뷰 진행 상태 머신
type InterviewStatus =
  | "CONNECTING" // 초기화 중
  | "AI_SPEAKING" // AI 질문 재생 + 타이핑
  | "USER_RECORDING" // 사용자 답변 녹음 중
  | "WAITING_AI"; // AI 응답 대기 중

const BAR_COUNT = 12;
const DEFAULT_HEIGHTS = Array<number>(BAR_COUNT).fill(4);
const SILENCE_THRESHOLD = 10; // 침묵으로 판단하는 평균 볼륨 기준
const SILENCE_DELAY_MS = 3000; // 침묵 감지 후 전송까지 대기 시간

// 현재 질문 정보
interface CurrentQuestion {
  interviewId: number;
  questionId: number;
  questionText: string;
  ttsAudioBase64: string;
}

export default function InterviewConversationPage() {
  return (
    <Suspense
      fallback={
        <div className={styles.container}>
          <p className={styles.statusText}>불러오는 중...</p>
        </div>
      }
    >
      <ConversationContent />
    </Suspense>
  );
}

function ConversationContent() {
  const searchParams = useSearchParams();
  const mode = searchParams.get("mode") ?? "AUTO";

  const [status, setStatus] = useState<InterviewStatus>("CONNECTING");
  const [displayText, setDisplayText] = useState("");
  const [waveHeights, setWaveHeights] = useState<number[]>(DEFAULT_HEIGHTS);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const questionRef = useRef<CurrentQuestion | null>(null);
  const statusRef = useRef<InterviewStatus>("CONNECTING");

  const streamRef = useRef<MediaStream | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const silenceTimerRef = useRef<NodeJS.Timeout | null>(null);
  const animationFrameRef = useRef<number | null>(null);
  const ttsAudioRef = useRef<HTMLAudioElement | null>(null);

  const { connect, sendAudio, disconnect } = useStompInterview();

  useEffect(() => {
    statusRef.current = status;
  }, [status]);

  useEffect(() => {
    initInterview();
    return () => cleanup();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const initInterview = async () => {
    try {
      const [data, stream, { apiUrl }] = await Promise.all([
        createInterview(mode === "AUTO"),
        navigator.mediaDevices.getUserMedia({ audio: true }),
        fetch("/api/ws-url").then((r) => r.json()),
      ]);

      questionRef.current = {
        interviewId: data.interviewId,
        questionId: data.questionId,
        questionText: data.interviewTitle,
        ttsAudioBase64: data.ttsAudioBase64,
      };

      streamRef.current = stream;
      const AudioContextClass =
        window.AudioContext || (window as any).webkitAudioContext;
      const audioContext = new AudioContextClass();
      const analyser = audioContext.createAnalyser();
      analyser.fftSize = 256;
      analyser.smoothingTimeConstant = 0.8;
      audioContext.createMediaStreamSource(stream).connect(analyser);
      audioContextRef.current = audioContext;
      analyserRef.current = analyser;

      const token = localStorage.getItem("accessToken") ?? "";

      connect(
        apiUrl,
        data.interviewId,
        token,
        (dto: FollowUpQuestionDTO) => {
          questionRef.current = {
            interviewId: dto.interviewId,
            questionId: dto.questionId,
            questionText: dto.followUpQuestion,
            ttsAudioBase64: dto.ttsAudioBase64,
          };
          setStatus("AI_SPEAKING");
        },
        () => setStatus("AI_SPEAKING"),
      );
    } catch (err) {
      console.error("인터뷰 초기화 실패:", err);
      setErrorMsg("인터뷰를 시작할 수 없습니다. 마이크 권한을 확인해 주세요.");
    }
  };

  useEffect(() => {
    if (status !== "AI_SPEAKING") return;
    const question = questionRef.current;
    if (!question) return;

    if (ttsAudioRef.current) {
      ttsAudioRef.current.pause();
      ttsAudioRef.current = null;
    }

    const text = question.questionText;
    setDisplayText("");
    let index = 0;
    const interval = setInterval(() => {
      setDisplayText(text.substring(0, index + 1));
      index++;
      if (index >= text.length) clearInterval(interval);
    }, 60);

    if (question.ttsAudioBase64) {
      const audio = playBase64Audio(question.ttsAudioBase64, () => {
        if (statusRef.current === "AI_SPEAKING") setStatus("USER_RECORDING");
      });
      ttsAudioRef.current = audio;
    } else {
      const timer = setTimeout(
        () => {
          if (statusRef.current === "AI_SPEAKING") setStatus("USER_RECORDING");
        },
        text.length * 60 + 1500,
      );
      return () => {
        clearInterval(interval);
        clearTimeout(timer);
      };
    }

    return () => {
      clearInterval(interval);
      ttsAudioRef.current?.pause();
    };
  }, [status]);

  useEffect(() => {
    if (status !== "USER_RECORDING") {
      stopVad();
      return;
    }
    startRecording();
    startVad();
    return () => stopVad();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status]);

  const startRecording = useCallback(() => {
    if (!streamRef.current) return;
    chunksRef.current = [];
    const recorder = new MediaRecorder(streamRef.current);
    recorder.ondataavailable = (e) => chunksRef.current.push(e.data);
    recorder.onstop = async () => {
      const blob = new Blob(chunksRef.current, { type: "audio/webm" });
      const audioData = await blobToBase64(blob);
      const question = questionRef.current;
      if (!question) return;
      sendAudio({
        interviewId: question.interviewId,
        questionId: question.questionId,
        audioData,
        isFinal: false,
      });
      setStatus("WAITING_AI");
    };
    recorder.start();
    recorderRef.current = recorder;
  }, [sendAudio]);

  const startVad = useCallback(() => {
    if (!analyserRef.current) return;
    const dataArray = new Uint8Array(analyserRef.current.frequencyBinCount);
    const loop = () => {
      if (statusRef.current !== "USER_RECORDING") return;
      if (!analyserRef.current) return;
      analyserRef.current.getByteFrequencyData(dataArray);
      const step = Math.floor(dataArray.length / BAR_COUNT);
      const heights = Array.from({ length: BAR_COUNT }, (_, i) => {
        const slice = dataArray.slice(i * step, (i + 1) * step);
        const avg = slice.reduce((a, b) => a + b, 0) / slice.length;
        return Math.max(4, Math.round((avg / 255) * 80));
      });
      setWaveHeights(heights);
      const avg = dataArray.reduce((p, c) => p + c, 0) / dataArray.length;
      if (avg < SILENCE_THRESHOLD) {
        if (!silenceTimerRef.current) {
          silenceTimerRef.current = setTimeout(() => {
            if (statusRef.current === "USER_RECORDING") {
              recorderRef.current?.stop();
            }
          }, SILENCE_DELAY_MS);
        }
      } else {
        if (silenceTimerRef.current) {
          clearTimeout(silenceTimerRef.current);
          silenceTimerRef.current = null;
        }
      }
      animationFrameRef.current = requestAnimationFrame(loop);
    };
    loop();
  }, []);

  const handleSubmit = () => {
    if (statusRef.current !== "USER_RECORDING") return;
    if (silenceTimerRef.current) {
      clearTimeout(silenceTimerRef.current);
      silenceTimerRef.current = null;
    }
    recorderRef.current?.stop();
  };

  const stopVad = () => {
    if (animationFrameRef.current) {
      cancelAnimationFrame(animationFrameRef.current);
      animationFrameRef.current = null;
    }
    if (silenceTimerRef.current) {
      clearTimeout(silenceTimerRef.current);
      silenceTimerRef.current = null;
    }
    setWaveHeights(DEFAULT_HEIGHTS);
  };

  const cleanup = () => {
    stopVad();
    recorderRef.current?.stop();
    streamRef.current?.getTracks().forEach((t) => t.stop());
    audioContextRef.current?.close();
    ttsAudioRef.current?.pause();
    disconnect();
  };

  const isFocusMode = status === "USER_RECORDING";

  return (
    <main
      className={`${styles.container} ${isFocusMode ? styles.focusMode : ""}`}
    >
      <div className={styles.orbWrapper}>
        <div className={`${styles.orb} ${styles.orb1}`} />
        <div className={`${styles.orb} ${styles.orb2}`} />
      </div>

      {status === "CONNECTING" && !errorMsg && (
        <div className={styles.analyzingUI}>
          <div className={styles.loadingBar} />
          <p className={styles.statusText}>연결 중입니다...</p>
        </div>
      )}

      {errorMsg && <p className={styles.errorText}>{errorMsg}</p>}

      {status !== "CONNECTING" && (
        <section className={styles.content}>
          <div className={styles.questionBox}>
            <h2 className={styles.questionText}>
              {displayText}
              {status === "AI_SPEAKING" && (
                <span className={styles.cursor}>|</span>
              )}
            </h2>
          </div>

          <div className={styles.visualizerArea}>
            {isFocusMode && (
              <button className={styles.recordingBtn} onClick={handleSubmit}>
                <div className={styles.blobWrapper}>
                  {/* 사진 느낌의 멀티 컬러 Blob 배경 */}
                  <div className={styles.blobBackground}>
                    <div className={`${styles.blobItem} ${styles.blobPurple}`} />
                    <div className={`${styles.blobItem} ${styles.blobOrange}`} />
                    <div className={`${styles.blobItem} ${styles.blobBlue}`} />
                    <div className={`${styles.blobItem} ${styles.blobPink}`} />
                  </div>

                  {/* 중앙 파형 비주얼라이저 */}
                  <div className={styles.waveContainer}>
                    {waveHeights.map((h, i) => (
                      <div
                        key={i}
                        className={styles.waveBar}
                        style={{ height: `${h}px` }}
                      />
                    ))}
                  </div>

                  {/* 하단 라벨 */}
                  <span className={styles.nextLabel}>NEXT &gt;</span>
                </div>
              </button>
            )}

            {status === "WAITING_AI" && (
              <div className={styles.analyzingUI}>
                <div className={styles.loadingBar} />
                <p className={styles.statusText}>
                  문장을 아카이빙하고 있습니다...
                </p>
              </div>
            )}
          </div>
        </section>
      )}
    </main>
  );
}

function playBase64Audio(
  base64: string,
  onEnded: () => void,
): HTMLAudioElement {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  const blob = new Blob([bytes], { type: "audio/mpeg" });
  const url = URL.createObjectURL(blob);
  const audio = new Audio(url);
  audio.onended = () => {
    URL.revokeObjectURL(url);
    onEnded();
  };
  audio.play().catch(console.error);
  return audio;
}

async function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve) => {
    const reader = new FileReader();
    reader.onloadend = () => {
      const result = reader.result as string;
      resolve(result.split(",")[1]);
    };
    reader.readAsDataURL(blob);
  });
}
