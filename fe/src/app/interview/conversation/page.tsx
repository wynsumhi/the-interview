"use client";

import { useState, useEffect, useRef, useCallback, Suspense } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import {
  FilesetResolver,
  GestureRecognizer,
  type GestureRecognizerResult,
} from "@mediapipe/tasks-vision";
import { useStompInterview } from "@/hooks/useStompInterview";
import { createInterview, exitInterview } from "@/services/interview";
import type { FollowUpQuestionDTO } from "@/services/interview";
import { startQuestionInterview } from "@/services/board";
import styles from "./page.module.css";

type InterviewStatus =
  | "CONNECTING"
  | "AI_SPEAKING"
  | "USER_RECORDING"
  | "WAITING_AI";

interface CurrentQuestion {
  interviewId: number;
  questionId: number;
  questionText: string;
  ttsAudioBase64: string;
}

const BAR_COUNT = 12;
const DEFAULT_HEIGHTS = Array<number>(BAR_COUNT).fill(4);
const SILENCE_THRESHOLD = 10;
const SILENCE_DELAY_MS = 3000;
const GESTURE_HOLD_MS = 1000;
const GESTURE_RESET_MS = 4000;
const GESTURE_SCORE_THRESHOLD = 0.75;
const VICTORY_GESTURE_NAME = "Victory";
const POINTING_UP_GESTURE_NAME = "Pointing_Up";
const NEXT_GESTURE_COOLDOWN_MS = 1200;
const GESTURE_INFERENCE_INTERVAL_MS = 120;
const MEDIAPIPE_WASM_PATH =
  "https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.34/wasm";
const MEDIAPIPE_GESTURE_MODEL_PATH =
  "https://storage.googleapis.com/mediapipe-models/gesture_recognizer/gesture_recognizer/float16/1/gesture_recognizer.task";

export default function InterviewConversationPage() {
  return (
    <Suspense
      fallback={
        <div className={styles.container}>
          <p className={styles.statusText}>인터뷰를 준비하고 있습니다...</p>
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
  const questionIdParam = searchParams.get("questionId");

  const [status, setStatus] = useState<InterviewStatus>("CONNECTING");
  const [displayText, setDisplayText] = useState("");
  const [waveHeights, setWaveHeights] = useState<number[]>(DEFAULT_HEIGHTS);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [isExiting, setIsExiting] = useState(false);
  const [gestureNotice, setGestureNotice] = useState<string | null>(null);
  const [nextGestureNotice, setNextGestureNotice] = useState<string | null>(null);

  const questionRef = useRef<CurrentQuestion | null>(null);
  const statusRef = useRef<InterviewStatus>("CONNECTING");

  const streamRef = useRef<MediaStream | null>(null);
  const cameraStreamRef = useRef<MediaStream | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const recorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const silenceTimerRef = useRef<NodeJS.Timeout | null>(null);
  const animationFrameRef = useRef<number | null>(null);
  const ttsAudioRef = useRef<HTMLAudioElement | null>(null);

  const gestureAnimationFrameRef = useRef<number | null>(null);
  const gestureRecognizerRef = useRef<GestureRecognizer | null>(null);
  const cameraVideoRef = useRef<HTMLVideoElement | null>(null);
  const gesturePhaseRef = useRef<0 | 1>(0);
  const gestureHoldStartedAtRef = useRef<number | null>(null);
  const gestureCandidateActiveRef = useRef(false);
  const gestureResetTimerRef = useRef<NodeJS.Timeout | null>(null);
  const nextGestureCooldownUntilRef = useRef(0);
  const nextGestureNoticeTimerRef = useRef<NodeJS.Timeout | null>(null);
  const handleExitRef = useRef<() => Promise<void>>(async () => {});
  const lastGestureInferenceAtRef = useRef(0);

  const { connect, sendAudio, disconnect } = useStompInterview();

  useEffect(() => {
    statusRef.current = status;
  }, [status]);

  const resetGestureState = useCallback(() => {
    gesturePhaseRef.current = 0;
    gestureHoldStartedAtRef.current = null;
    gestureCandidateActiveRef.current = false;
    if (gestureResetTimerRef.current) {
      clearTimeout(gestureResetTimerRef.current);
      gestureResetTimerRef.current = null;
    }
    setGestureNotice(null);
  }, []);

  const showNextGestureNotice = useCallback((message: string) => {
    if (nextGestureNoticeTimerRef.current) {
      clearTimeout(nextGestureNoticeTimerRef.current);
    }
    setNextGestureNotice(message);
    nextGestureNoticeTimerRef.current = setTimeout(() => {
      setNextGestureNotice(null);
      nextGestureNoticeTimerRef.current = null;
    }, 1800);
  }, []);

  const initGestureRecognizer = useCallback(async () => {
    if (gestureRecognizerRef.current) return;

    const vision = await FilesetResolver.forVisionTasks(MEDIAPIPE_WASM_PATH);
    gestureRecognizerRef.current = await GestureRecognizer.createFromOptions(
      vision,
      {
        baseOptions: {
          modelAssetPath: MEDIAPIPE_GESTURE_MODEL_PATH,
        },
        runningMode: "VIDEO",
        numHands: 1,
      },
    );
  }, []);

  const scheduleGestureReset = useCallback(() => {
    if (gestureResetTimerRef.current) {
      clearTimeout(gestureResetTimerRef.current);
    }
    gestureResetTimerRef.current = setTimeout(() => {
      resetGestureState();
    }, GESTURE_RESET_MS);
  }, [resetGestureState]);

  const getTopGesture = useCallback(
    (result: GestureRecognizerResult) => {
      const [firstHand] = result.gestures;
      const [topGesture] = firstHand ?? [];
      if (!topGesture) return null;
      if (topGesture.score < GESTURE_SCORE_THRESHOLD) return null;
      return topGesture;
    },
    [],
  );

  const handleGestureResult = useCallback(
    (result: GestureRecognizerResult, now: number) => {
      if (isExiting || statusRef.current === "CONNECTING") {
        resetGestureState();
        return;
      }

      if (
        statusRef.current !== "AI_SPEAKING" &&
        statusRef.current !== "USER_RECORDING"
      ) {
        resetGestureState();
        return;
      }

      const topGesture = getTopGesture(result);
      const isPointingUp = topGesture?.categoryName === POINTING_UP_GESTURE_NAME;
      const isVictory = topGesture?.categoryName === VICTORY_GESTURE_NAME;

      if (
        isPointingUp &&
        statusRef.current === "USER_RECORDING" &&
        now >= nextGestureCooldownUntilRef.current
      ) {
        nextGestureCooldownUntilRef.current = now + NEXT_GESTURE_COOLDOWN_MS;
        showNextGestureNotice(
          "검지 제스처가 감지되었습니다. 다음 질문 연결은 추후 활성화됩니다.",
        );
      }

      if (!isVictory) {
        gestureCandidateActiveRef.current = false;
        gestureHoldStartedAtRef.current = null;
        return;
      }

      if (!gestureCandidateActiveRef.current) {
        gestureCandidateActiveRef.current = true;
        gestureHoldStartedAtRef.current = now;
        return;
      }

      const holdStartedAt = gestureHoldStartedAtRef.current;
      if (!holdStartedAt || now - holdStartedAt < GESTURE_HOLD_MS) return;

      if (gesturePhaseRef.current === 0) {
        gesturePhaseRef.current = 1;
        gestureCandidateActiveRef.current = false;
        gestureHoldStartedAtRef.current = null;
        setGestureNotice(
          "브이 제스처가 감지되었습니다. 종료하려면 한 번 더 유지하세요.",
        );
        scheduleGestureReset();
        return;
      }

      if (gestureResetTimerRef.current) {
        clearTimeout(gestureResetTimerRef.current);
        gestureResetTimerRef.current = null;
      }
      void handleExitRef.current();
    },
    [
      getTopGesture,
      isExiting,
      resetGestureState,
      scheduleGestureReset,
      showNextGestureNotice,
    ],
  );

  const startGestureLoop = useCallback(() => {
    const loop = () => {
      const recognizer = gestureRecognizerRef.current;
      const video = cameraVideoRef.current;
      const shouldDetectGesture =
        statusRef.current === "AI_SPEAKING" ||
        statusRef.current === "USER_RECORDING";

      if (
        recognizer &&
        shouldDetectGesture &&
        video &&
        video.readyState >= HTMLMediaElement.HAVE_ENOUGH_DATA &&
        video.videoWidth > 0 &&
        video.videoHeight > 0 &&
        !video.paused &&
        !video.ended
      ) {
        try {
          const now = performance.now();
          if (
            now - lastGestureInferenceAtRef.current <
            GESTURE_INFERENCE_INTERVAL_MS
          ) {
            gestureAnimationFrameRef.current = requestAnimationFrame(loop);
            return;
          }
          lastGestureInferenceAtRef.current = now;
          const result = recognizer.recognizeForVideo(video, now);
          handleGestureResult(result, now);
        } catch {
          // MediaPipe video pipeline can throw briefly while the camera stream stabilizes.
        }
      }

      gestureAnimationFrameRef.current = requestAnimationFrame(loop);
    };

    if (!gestureAnimationFrameRef.current) {
      gestureAnimationFrameRef.current = requestAnimationFrame(loop);
    }
  }, [handleGestureResult]);

  useEffect(() => {
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
      const interviewDataPromise = questionIdParam
        ? startQuestionInterview(Number(questionIdParam)).then((r) => ({
            interviewId: r.interview.interviewId,
            questionId: r.interview.questionId,
            interviewTitle: r.interview.interviewTitle,
            ttsAudioBase64: r.interview.ttsAudioBase64,
          }))
        : createInterview(mode === "AUTO");

      const [data, stream, cameraStream, { apiUrl }] = await Promise.all([
        interviewDataPromise,
        navigator.mediaDevices.getUserMedia({ audio: true }),
        navigator.mediaDevices.getUserMedia({
          video: {
            facingMode: "user",
            width: { ideal: 640 },
            height: { ideal: 480 },
          },
        }),
        fetch("/api/ws-url").then((r) => r.json()),
      ]);

      if (isCancelled()) {
        stream.getTracks().forEach((track) => track.stop());
        cameraStream.getTracks().forEach((track) => track.stop());
        return;
      }

      questionRef.current = {
        interviewId: data.interviewId,
        questionId: data.questionId,
        questionText: stripMarkdown(data.interviewTitle),
        ttsAudioBase64: data.ttsAudioBase64,
      };

      streamRef.current = stream;
      cameraStreamRef.current = cameraStream;

      const AudioContextClass =
        window.AudioContext || (window as typeof window & { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
      const audioContext = new AudioContextClass();
      const analyser = audioContext.createAnalyser();
      analyser.fftSize = 256;
      analyser.smoothingTimeConstant = 0.8;
      audioContext.createMediaStreamSource(stream).connect(analyser);
      audioContextRef.current = audioContext;
      analyserRef.current = analyser;

      if (cameraVideoRef.current) {
        cameraVideoRef.current.srcObject = cameraStream;
        cameraVideoRef.current.muted = true;
        cameraVideoRef.current.playsInline = true;
        await cameraVideoRef.current.play();
      }

      await initGestureRecognizer();
      startGestureLoop();

      const token = localStorage.getItem("accessToken") ?? "";

      connect(
        apiUrl,
        data.interviewId,
        token,
        (dto: FollowUpQuestionDTO) => {
          questionRef.current = {
            interviewId: dto.interviewId,
            questionId: dto.questionId,
            questionText: stripMarkdown(dto.followUpQuestion),
            ttsAudioBase64: dto.ttsAudioBase64,
          };
          resetGestureState();
          setStatus("AI_SPEAKING");
        },
        () => {
          resetGestureState();
          setStatus("AI_SPEAKING");
        },
      );
    } catch (err) {
      if (!isCancelled()) {
        console.error("인터뷰 초기화 실패:", err);
        setErrorMsg(
          "인터뷰를 시작하지 못했습니다. 마이크와 카메라 권한을 확인해주세요.",
        );
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
      index += 1;
      if (index >= text.length) clearInterval(interval);
    }, 60);

    if (question.ttsAudioBase64) {
      const audio = playBase64Audio(question.ttsAudioBase64, () => {
        if (statusRef.current === "AI_SPEAKING") setStatus("USER_RECORDING");
      });
      ttsAudioRef.current = audio;
    } else {
      const timer = setTimeout(() => {
        if (statusRef.current === "AI_SPEAKING") setStatus("USER_RECORDING");
      }, text.length * 60 + 1500);

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
    recorder.ondataavailable = (event) => chunksRef.current.push(event.data);
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
      resetGestureState();
      setStatus("WAITING_AI");
    };
    recorder.start();
    recorderRef.current = recorder;
  }, [resetGestureState, sendAudio]);

  const startVad = useCallback(() => {
    if (!analyserRef.current) return;
    const dataArray = new Uint8Array(analyserRef.current.frequencyBinCount);
    const loop = () => {
      if (statusRef.current !== "USER_RECORDING") return;
      if (!analyserRef.current) return;

      analyserRef.current.getByteFrequencyData(dataArray);
      const step = Math.floor(dataArray.length / BAR_COUNT);
      const heights = Array.from({ length: BAR_COUNT }, (_, index) => {
        const slice = dataArray.slice(index * step, (index + 1) * step);
        const avg = slice.reduce((sum, value) => sum + value, 0) / slice.length;
        return Math.max(4, Math.round((avg / 255) * 80));
      });
      setWaveHeights(heights);

      const avg = dataArray.reduce((sum, value) => sum + value, 0) / dataArray.length;
      if (avg < SILENCE_THRESHOLD) {
        if (!silenceTimerRef.current) {
          silenceTimerRef.current = setTimeout(() => {
            if (statusRef.current === "USER_RECORDING") {
              recorderRef.current?.stop();
            }
          }, SILENCE_DELAY_MS);
        }
      } else if (silenceTimerRef.current) {
        clearTimeout(silenceTimerRef.current);
        silenceTimerRef.current = null;
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

  const stopVad = useCallback(() => {
    if (animationFrameRef.current) {
      cancelAnimationFrame(animationFrameRef.current);
      animationFrameRef.current = null;
    }
    if (silenceTimerRef.current) {
      clearTimeout(silenceTimerRef.current);
      silenceTimerRef.current = null;
    }
    setWaveHeights(DEFAULT_HEIGHTS);
  }, []);

  const cleanup = useCallback(() => {
    resetGestureState();
    if (nextGestureNoticeTimerRef.current) {
      clearTimeout(nextGestureNoticeTimerRef.current);
      nextGestureNoticeTimerRef.current = null;
    }
    setNextGestureNotice(null);
    lastGestureInferenceAtRef.current = 0;
    stopVad();

    if (gestureAnimationFrameRef.current) {
      cancelAnimationFrame(gestureAnimationFrameRef.current);
      gestureAnimationFrameRef.current = null;
    }

    recorderRef.current?.stop();
    recorderRef.current = null;

    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;

    cameraStreamRef.current?.getTracks().forEach((track) => track.stop());
    cameraStreamRef.current = null;

    audioContextRef.current?.close();
    audioContextRef.current = null;

    ttsAudioRef.current?.pause();
    ttsAudioRef.current = null;

    if (cameraVideoRef.current) {
      cameraVideoRef.current.pause();
      cameraVideoRef.current.srcObject = null;
    }

    gestureRecognizerRef.current?.close();
    gestureRecognizerRef.current = null;
    disconnect();
  }, [disconnect, resetGestureState, stopVad]);

  async function handleExit() {
    if (isExiting) return;
    setIsExiting(true);
    resetGestureState();

    const question = questionRef.current;

    if (recorderRef.current) {
      recorderRef.current.onstop = null;
      recorderRef.current.stop();
    }
    ttsAudioRef.current?.pause();
    cleanup();

    try {
      if (question) {
        await exitInterview(question.interviewId, question.questionId);
      }
    } catch (err) {
      console.warn("인터뷰 종료 요청 실패:", err);
    } finally {
      router.push("/archive");
    }
  }
  handleExitRef.current = handleExit;

  const isFocusMode = status === "USER_RECORDING";

  return (
    <main
      className={`${styles.container} ${isFocusMode ? styles.focusMode : ""}`}
    >
      <div className={styles.orbWrapper}>
        <div className={`${styles.orb} ${styles.orb1}`} />
        <div className={`${styles.orb} ${styles.orb2}`} />
      </div>
      <video ref={cameraVideoRef} className={styles.hiddenVideo} playsInline />

      {status === "CONNECTING" && !errorMsg && (
        <div className={styles.analyzingUI}>
          <div className={styles.loadingBar} />
          <p className={styles.statusText}>인터뷰를 준비하고 있습니다...</p>
        </div>
      )}

      {errorMsg && <p className={styles.errorText}>{errorMsg}</p>}

      {status !== "CONNECTING" && (
        <section className={styles.content}>
          <button className={styles.exitBtn} onClick={() => void handleExit()} disabled={isExiting}>
            {isExiting ? "종료 중..." : "인터뷰 종료"}
          </button>
          {gestureNotice && (
            <p className={styles.gestureNotice}>{gestureNotice}</p>
          )}
          {nextGestureNotice && (
            <p className={styles.swipeNotice}>{nextGestureNotice}</p>
          )}

          <div className={styles.questionBox}>
            <h2 className={styles.questionText}>
              {displayText}
              {status === "AI_SPEAKING" && <span className={styles.cursor}>|</span>}
            </h2>
          </div>

          <div className={styles.visualizerArea}>
            {isFocusMode && (
              <button className={styles.recordingBtn} onClick={handleSubmit}>
                <div className={styles.blobWrapper}>
                  <svg
                    className={styles.blobSvg}
                    viewBox="0 0 435 416"
                    xmlns="http://www.w3.org/2000/svg"
                  >
                    <defs>
                      <filter
                        id="b0"
                        x="90.5"
                        y="47"
                        width="300"
                        height="309"
                        filterUnits="userSpaceOnUse"
                      >
                        <feGaussianBlur stdDeviation="17.135" />
                      </filter>
                      <filter
                        id="b1"
                        x="28"
                        y="110.5"
                        width="305"
                        height="276"
                        filterUnits="userSpaceOnUse"
                      >
                        <feGaussianBlur stdDeviation="17.135" />
                      </filter>
                      <filter
                        id="b2"
                        x="90"
                        y="0"
                        width="344.5"
                        height="365.5"
                        filterUnits="userSpaceOnUse"
                      >
                        <feGaussianBlur stdDeviation="17.135" />
                      </filter>
                      <filter
                        id="b3"
                        x="0"
                        y="100"
                        width="350.5"
                        height="315.5"
                        filterUnits="userSpaceOnUse"
                      >
                        <feGaussianBlur stdDeviation="17.135" />
                      </filter>
                      <linearGradient
                        id="g0"
                        x1="166.875"
                        y1="113.673"
                        x2="292.5"
                        y2="281.27"
                        gradientUnits="userSpaceOnUse"
                      >
                        <stop stopColor="#6981FF" />
                        <stop offset="1" stopColor="#C68059" stopOpacity="0" />
                      </linearGradient>
                      <linearGradient
                        id="g1"
                        x1="154.27"
                        y1="192.269"
                        x2="154.27"
                        y2="352.269"
                        gradientUnits="userSpaceOnUse"
                      >
                        <stop stopColor="#FFB6B6" stopOpacity="0.62" />
                        <stop offset="1" stopColor="#6141E1" stopOpacity="0" />
                      </linearGradient>
                      <linearGradient
                        id="g2"
                        x1="174.469"
                        y1="74.286"
                        x2="331.056"
                        y2="275.965"
                        gradientUnits="userSpaceOnUse"
                      >
                        <stop stopColor="#6981FF" />
                        <stop offset="1" stopColor="#C68059" stopOpacity="0" />
                      </linearGradient>
                      <linearGradient
                        id="g3"
                        x1="143.969"
                        y1="190.811"
                        x2="143.969"
                        y2="381.269"
                        gradientUnits="userSpaceOnUse"
                      >
                        <stop stopColor="#FFB6B6" stopOpacity="0.62" />
                        <stop offset="1" stopColor="#6141E1" stopOpacity="0" />
                      </linearGradient>
                    </defs>
                    <path
                      filter="url(#b0)"
                      d="M356.27 254.77C356.27 304.475 290.475 321.77 240.77 321.77C191.064 321.77 124.77 295.475 124.77 245.77C124.77 196.064 143.564 81.2695 193.27 81.2695C242.975 81.2695 356.27 205.064 356.27 254.77Z"
                      fill="url(#g0)"
                    />
                    <path
                      filter="url(#b1)"
                      d="M298.77 296.769C298.77 340.952 198.452 352.269 154.27 352.269C110.087 352.269 62.2695 257.452 62.2695 213.269C62.2695 169.086 110.087 144.769 154.27 144.769C198.452 144.769 298.77 252.587 298.77 296.769Z"
                      fill="url(#g1)"
                    />
                    <path
                      filter="url(#b2)"
                      d="M400.27 248.529C400.27 309.912 321.828 331.27 262.568 331.27C203.307 331.27 124.27 298.798 124.27 237.415C124.27 176.032 146.677 34.2695 205.937 34.2695C265.197 34.2695 400.27 187.147 400.27 248.529Z"
                      fill="url(#g2)"
                    />
                    <path
                      filter="url(#b3)"
                      d="M316.27 315.205C316.27 367.798 196.652 381.27 143.969 381.27C91.2863 381.27 34.2695 268.402 34.2695 215.809C34.2695 163.216 91.2863 134.27 143.969 134.27C196.652 134.27 316.27 262.611 316.27 315.205Z"
                      fill="url(#g3)"
                    />
                  </svg>

                  <div className={styles.waveContainer}>
                    {waveHeights.map((height, index) => (
                      <div
                        key={index}
                        className={styles.waveBar}
                        style={{ height: `${height}px` }}
                      />
                    ))}
                  </div>

                  <span className={styles.nextLabel}>NEXT &gt;</span>
                </div>
              </button>
            )}

            {status === "WAITING_AI" && (
              <div className={styles.analyzingUI}>
                <div className={styles.loadingBar} />
                <p className={styles.statusText}>
                  다음 질문을 생성하고 있습니다...
                </p>
              </div>
            )}
          </div>
        </section>
      )}
    </main>
  );
}

function stripMarkdown(text: string): string {
  return text
    .replace(/#{1,6}\s*/g, "")
    .replace(/\*{1,2}([^*]*)\*{1,2}/g, "$1")
    .replace(/_{1,2}([^_]*)_{1,2}/g, "$1")
    .replace(/`{1,3}[^`]*`{1,3}/g, "")
    .replace(/^[-*>]\s+/gm, "")
    .replace(/[()[\]]/g, "")
    .trim();
}

function playBase64Audio(
  base64: string,
  onEnded: () => void,
): HTMLAudioElement {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
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
