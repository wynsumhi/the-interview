"use client";

import { useState, useEffect, useRef, useCallback, Suspense } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import { useStompInterview } from "@/hooks/useStompInterview";
import { createInterview, exitInterview } from "@/services/interview";
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
  const router = useRouter();
  const mode = searchParams.get("mode") ?? "AUTO";

  const [status, setStatus] = useState<InterviewStatus>("CONNECTING");
  const [displayText, setDisplayText] = useState("");
  const [waveHeights, setWaveHeights] = useState<number[]>(DEFAULT_HEIGHTS);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [isExiting, setIsExiting] = useState(false);

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
    // cancelled 플래그: React StrictMode의 이중 실행 방지
    // StrictMode는 개발 환경에서 mount → cleanup → remount 순서로 effect를 두 번 실행함
    // async인 initInterview는 cleanup이 호출돼도 실행이 취소되지 않으므로
    // Promise.all 완료 후 cancelled를 확인해 connect() 중복 호출을 막음
    let cancelled = false;

    initInterview(() => cancelled);
    return () => {
      cancelled = true;
      cleanup();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const initInterview = async (isCancelled: () => boolean) => {
    try {
      const [data, stream, { apiUrl }] = await Promise.all([
        createInterview(mode === "AUTO"),
        navigator.mediaDevices.getUserMedia({ audio: true }),
        fetch("/api/ws-url").then((r) => r.json()),
      ]);

      // async 작업 완료 후 컴포넌트가 이미 cleanup된 경우 연결하지 않음
      if (isCancelled()) {
        stream.getTracks().forEach((t) => t.stop());
        return;
      }

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
      if (!isCancelled()) {
        console.error("인터뷰 초기화 실패:", err);
        setErrorMsg("인터뷰를 시작할 수 없습니다. 마이크 권한을 확인해 주세요.");
      }
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

  // 인터뷰 종료 버튼 핸들러
  // 1) 진행 중인 녹음/오디오 정리 → 2) POST /interview/exit 호출 → 3) 리소스 cleanup → 4) /archive로 이동
  const handleExit = async () => {
    if (isExiting) return; // 중복 클릭 방지
    setIsExiting(true);

    // 녹음 중이었다면 onstop 핸들러를 먼저 제거 후 중단
    // onstop을 그대로 두면 stop() 호출 시 sendAudio + setStatus("WAITING_AI")가
    // 실행되어 종료 중에 불필요한 상태 전환과 백엔드 요청이 발생함
    if (recorderRef.current) {
      recorderRef.current.onstop = null;
      recorderRef.current.stop();
    }
    ttsAudioRef.current?.pause();

    const question = questionRef.current;
    try {
      if (question) {
        // 서버에 종료 신호 전송 → AI가 요약 제목 생성해 반환 (현재는 사용하지 않고 archive로 이동)
        await exitInterview(question.interviewId, question.questionId);
      }
    } catch (err) {
      // 종료 API 실패해도 로컬 cleanup 후 이동
      console.error("인터뷰 종료 요청 실패:", err);
    } finally {
      cleanup();
      router.push("/archive");
    }
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
          {/* 인터뷰 종료 버튼 - 우상단 고정
               WAITING_AI: 음성 전송 후 백엔드 처리 중 → 도중에 종료 시 데이터 유실 위험
               isExiting: 종료 API 호출 중 → 중복 클릭 방지 */}
          <button
            className={styles.exitBtn}
            onClick={handleExit}
            disabled={isExiting || status === "WAITING_AI"}
          >
            {isExiting ? "종료 중..." : "인터뷰 종료"}
          </button>
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
                  {/* 디자인 blob SVG */}
                  <svg className={styles.blobSvg} viewBox="0 0 435 416" xmlns="http://www.w3.org/2000/svg">
                    <defs>
                      <filter id="b0" x="90.5" y="47" width="300" height="309" filterUnits="userSpaceOnUse">
                        <feGaussianBlur stdDeviation="17.135" />
                      </filter>
                      <filter id="b1" x="28" y="110.5" width="305" height="276" filterUnits="userSpaceOnUse">
                        <feGaussianBlur stdDeviation="17.135" />
                      </filter>
                      <filter id="b2" x="90" y="0" width="344.5" height="365.5" filterUnits="userSpaceOnUse">
                        <feGaussianBlur stdDeviation="17.135" />
                      </filter>
                      <filter id="b3" x="0" y="100" width="350.5" height="315.5" filterUnits="userSpaceOnUse">
                        <feGaussianBlur stdDeviation="17.135" />
                      </filter>
                      <linearGradient id="g0" x1="166.875" y1="113.673" x2="292.5" y2="281.27" gradientUnits="userSpaceOnUse">
                        <stop stopColor="#6981FF" />
                        <stop offset="1" stopColor="#C68059" stopOpacity="0" />
                      </linearGradient>
                      <linearGradient id="g1" x1="154.27" y1="192.269" x2="154.27" y2="352.269" gradientUnits="userSpaceOnUse">
                        <stop stopColor="#FFB6B6" stopOpacity="0.62" />
                        <stop offset="1" stopColor="#6141E1" stopOpacity="0" />
                      </linearGradient>
                      <linearGradient id="g2" x1="174.469" y1="74.286" x2="331.056" y2="275.965" gradientUnits="userSpaceOnUse">
                        <stop stopColor="#6981FF" />
                        <stop offset="1" stopColor="#C68059" stopOpacity="0" />
                      </linearGradient>
                      <linearGradient id="g3" x1="143.969" y1="190.811" x2="143.969" y2="381.269" gradientUnits="userSpaceOnUse">
                        <stop stopColor="#FFB6B6" stopOpacity="0.62" />
                        <stop offset="1" stopColor="#6141E1" stopOpacity="0" />
                      </linearGradient>
                    </defs>
                    <path filter="url(#b0)" d="M356.27 254.77C356.27 304.475 290.475 321.77 240.77 321.77C191.064 321.77 124.77 295.475 124.77 245.77C124.77 196.064 143.564 81.2695 193.27 81.2695C242.975 81.2695 356.27 205.064 356.27 254.77Z" fill="url(#g0)" />
                    <path filter="url(#b1)" d="M298.77 296.769C298.77 340.952 198.452 352.269 154.27 352.269C110.087 352.269 62.2695 257.452 62.2695 213.269C62.2695 169.086 110.087 144.769 154.27 144.769C198.452 144.769 298.77 252.587 298.77 296.769Z" fill="url(#g1)" />
                    <path filter="url(#b2)" d="M400.27 248.529C400.27 309.912 321.828 331.27 262.568 331.27C203.307 331.27 124.27 298.798 124.27 237.415C124.27 176.032 146.677 34.2695 205.937 34.2695C265.197 34.2695 400.27 187.147 400.27 248.529Z" fill="url(#g2)" />
                    <path filter="url(#b3)" d="M316.27 315.205C316.27 367.798 196.652 381.27 143.969 381.27C91.2863 381.27 34.2695 268.402 34.2695 215.809C34.2695 163.216 91.2863 134.27 143.969 134.27C196.652 134.27 316.27 262.611 316.27 315.205Z" fill="url(#g3)" />
                  </svg>

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
