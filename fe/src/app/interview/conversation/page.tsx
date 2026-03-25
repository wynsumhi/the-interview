"use client";

import { useState, useEffect, useRef, useCallback, Suspense } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import { useQueryClient } from "@tanstack/react-query";
import { useStompInterview } from "@/hooks/useStompInterview";
import { createInterview, exitInterviewV2 } from "@/services/interview";
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

// 백엔드(Spring + FFmpeg)가 기대하는 포맷: audio/webm (Opus 코덱 우선)
// isTypeSupported로 브라우저 지원 여부를 확인한 뒤 가능한 한 고품질 코덱을 선택
const RECORDER_MIME = typeof MediaRecorder !== "undefined" && MediaRecorder.isTypeSupported("audio/webm;codecs=opus")
  ? "audio/webm;codecs=opus"
  : "audio/webm"; // 프로덕션 Chrome/Edge는 항상 webm+opus 지원

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
  const queryClient = useQueryClient();
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
  // ── 듀얼 MediaRecorder ──────────────────────────────────────────────────
  // chunkRecorder: Stop-and-Start 사이클로 STT 청크를 /app/interview/audio/chunk로 전송
  //   → 매 2초마다 stop() 후 새 인스턴스로 start() → 각 청크가 독립 WebM(EBML 헤더 포함)
  // fullRecorder:  전체 답변 오디오를 누적해 감정 분석 및 /next|/exitV2로 전송
  const chunkRecorderRef = useRef<MediaRecorder | null>(null);
  const fullRecorderRef = useRef<MediaRecorder | null>(null);
  // Stop-and-Start 사이클 활성 여부 — false로 설정하면 다음 사이클 시작을 차단
  const chunkCycleActiveRef = useRef(false);
  // 청크 순번 - 각 녹음 세션 시작 시 0으로 초기화 (서버 측 동기화 기준)
  const chunkIndexRef = useRef<number>(0);
  // fullRecorder 누적 버퍼
  const fullChunksRef = useRef<Blob[]>([]);
  // fullRecorder.onstop 의 전송 목적지: "next"(꼬리질문) | "exit"(인터뷰 종료)
  const finalDestRef = useRef<"next" | "exit">("next");
  // ────────────────────────────────────────────────────────────────────────
  const silenceTimerRef = useRef<NodeJS.Timeout | null>(null);
  const animationFrameRef = useRef<number | null>(null);
  const ttsAudioRef = useRef<HTMLAudioElement | null>(null);

  const { connect, sendChunk, sendNext, disconnect } = useStompInterview();

  useEffect(() => {
    statusRef.current = status;
  }, [status]);

  useEffect(() => {
    // AbortController로 StrictMode 이중 실행 방지
    // StrictMode는 개발 환경에서 mount → cleanup → remount 순서로 effect를 두 번 실행함
    // cleanup 시 controller.abort()를 호출하면 진행 중인 POST 요청이 취소되고,
    // remount 시 새 controller로 요청을 재시도해 정상적으로 WebSocket까지 연결됨
    const controller = new AbortController();
    initInterview(controller.signal);
    return () => {
      controller.abort();
      cleanup();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const initInterview = async (signal: AbortSignal) => {
    try {
      const [data, stream, { apiUrl }] = await Promise.all([
        createInterview(mode === "AUTO", signal),
        navigator.mediaDevices.getUserMedia({ audio: true }),
        fetch("/api/ws-url").then((r) => r.json()),
      ]);

      // async 작업 완료 후 컴포넌트가 이미 cleanup된 경우 연결하지 않음
      if (signal.aborted) {
        stream.getTracks().forEach((t) => t.stop());
        return;
      }

      questionRef.current = {
        interviewId: data.interviewId,
        questionId: data.questionId,
        questionText: stripMarkdown(data.interviewTitle),
        ttsAudioBase64: data.ttsAudioBase64,
      };

      // [DIAG] 마이크 스트림 상태 검증
      const tracks = stream.getAudioTracks();
      console.log("[DIAG] 마이크 트랙 수:", tracks.length);
      tracks.forEach((t, i) =>
        console.log(`[DIAG] 트랙[${i}] label="${t.label}" enabled=${t.enabled} readyState=${t.readyState}`)
      );

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
            questionText: stripMarkdown(dto.followUpQuestion),
            ttsAudioBase64: dto.ttsAudioBase64,
          };
          setStatus("AI_SPEAKING");
        },
        () => setStatus("AI_SPEAKING"),
      );
    } catch (err) {
      if (!signal.aborted) {
        console.error("인터뷰 초기화 실패:", err);
        setErrorMsg(
          "인터뷰를 시작할 수 없습니다. 마이크 권한을 확인해 주세요.",
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

    // 각 녹음 세션 시작 시 청크 카운터·버퍼·목적지 초기화
    chunkIndexRef.current = 0;
    fullChunksRef.current = [];
    finalDestRef.current = "next";

    // ── chunkRecorder: Stop-and-Start 사이클 ──────────────────────────
    // timeslice 대신 2초마다 stop() → 새 인스턴스로 start() 반복
    // → 각 청크가 EBML 헤더를 포함한 독립 WebM 파일 → Whisper가 단독으로 디코딩 가능
    chunkCycleActiveRef.current = true;

    const startChunkCycle = () => {
      if (!streamRef.current || !chunkCycleActiveRef.current) return;

      const recorder = new MediaRecorder(streamRef.current, { mimeType: RECORDER_MIME });

      recorder.ondataavailable = async (e) => {
        console.log(`[DIAG] chunkRecorder.ondataavailable — blob.size=${e.data.size}bytes`);
        if (e.data.size === 0) { console.warn("[DIAG] ⚠ 청크 Blob 크기 0"); return; }
        const index = ++chunkIndexRef.current;
        const audioData = await blobToBase64(e.data);
        console.log(`[DIAG] chunk[${index}] base64 길이=${audioData.length} 앞부분="${audioData.substring(0, 20)}..."`);
        const question = questionRef.current;
        if (!question) return;
        console.log(`[DIAG] sendChunk → interviewId=${question.interviewId} questionId=${question.questionId} chunkIndex=${index}`);
        sendChunk({
          interviewId: question.interviewId,
          questionId: question.questionId,
          audioData,
          chunkIndex: index,
        });
      };

      // stop() 후 사이클이 아직 활성이면 새 인스턴스로 재시작
      recorder.onstop = () => {
        if (chunkCycleActiveRef.current) startChunkCycle();
      };

      recorder.start();
      chunkRecorderRef.current = recorder;

      // 3초 후 중단 → ondataavailable(완전한 WebM) + onstop(재시작 여부 결정) 순서로 트리거됨
      setTimeout(() => {
        if (recorder.state === "recording") recorder.stop();
      }, 3000);
    };

    startChunkCycle();

    // ── fullRecorder: 전체 답변 오디오 누적 ────────────────────────────
    // onstop 시점에 totalChunk(String)와 함께 /next 또는 /exitV2로 전송
    const fullRecorder = new MediaRecorder(streamRef.current, { mimeType: RECORDER_MIME });
    fullRecorder.ondataavailable = (e) => {
      // [DIAG] fullRecorder 누적 Blob 검증
      console.log(`[DIAG] fullRecorder.ondataavailable — blob.size=${e.data.size}bytes`);
      if (e.data.size > 0) fullChunksRef.current.push(e.data);
    };
    fullRecorder.onstop = async () => {
      const blob = new Blob(fullChunksRef.current, { type: RECORDER_MIME });
      // [DIAG] 최종 Blob 크기 검증
      console.log(`[DIAG] fullRecorder.onstop — 누적 chunks=${fullChunksRef.current.length} finalBlob.size=${blob.size}bytes totalChunk="${String(chunkIndexRef.current)}"(type=${typeof String(chunkIndexRef.current)})`);
      if (blob.size === 0) { console.error("[DIAG] ✗ finalBlob 크기 0 — 오디오 데이터 없음"); }
      const audioData = await blobToBase64(blob);
      // [DIAG] 최종 Base64 검증
      console.log(`[DIAG] final base64 길이=${audioData.length} 앞부분="${audioData.substring(0, 20)}..."`);
      const question = questionRef.current;
      if (!question) return;

      const payload = {
        interviewId: question.interviewId,
        questionId: question.questionId,
        answerAudio: audioData, // BE TotalChunkRequest.answerAudio (byte[]) 필드명과 일치
        // totalChunk는 String 타입으로 전송해야 서버 측 동기화 오류를 방지
        totalChunk: String(chunkIndexRef.current),
      };

      if (finalDestRef.current === "exit") {
        console.log(`[DIAG] exitInterviewV2 → interviewId=${question.interviewId} questionId=${question.questionId} totalChunk="${payload.totalChunk}"`);
        await exitInterviewV2(payload);
      } else {
        console.log(`[DIAG] sendNext → interviewId=${question.interviewId} questionId=${question.questionId} totalChunk="${payload.totalChunk}"`);
        sendNext(payload);
        setStatus("WAITING_AI");
      }
    };
    fullRecorder.start();
    fullRecorderRef.current = fullRecorder;
  }, [sendChunk, sendNext]);

  // ── 순서 보장 중단 시퀀스 ────────────────────────────────────────────────
  // 1) chunkRecorder.stop() → 마지막 ondataavailable 트리거 (Base64 변환 + sendChunk)
  // 2) 300ms 대기 → 마지막 청크가 WebSocket으로 전송 완료될 때까지 여유 시간 확보
  // 3) fullRecorder.stop() → onstop에서 totalChunk와 함께 /next|/exitV2 전송
  //    이 시점에 서버는 이미 모든 chunkIndex(1~totalChunk)를 수신한 상태여야 함
  const stopRecording = useCallback(() => {
    // 사이클 차단 → onstop에서 재시작 방지
    chunkCycleActiveRef.current = false;
    // 현재 진행 중인 청크 강제 완료 → ondataavailable로 마지막 WebM 청크 전송
    chunkRecorderRef.current?.stop();
    // 마지막 청크 전송 완료 후 fullRecorder 중단
    setTimeout(() => {
      fullRecorderRef.current?.stop();
    }, 300);
  }, []);

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
              stopRecording();
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
  }, [stopRecording]);

  const handleSubmit = () => {
    if (statusRef.current !== "USER_RECORDING") return;
    if (silenceTimerRef.current) {
      clearTimeout(silenceTimerRef.current);
      silenceTimerRef.current = null;
    }
    stopRecording();
  };

  // 인터뷰 종료 버튼 핸들러
  // 1) 녹음 중이면 전체 오디오를 /exitV2로 전송 → 2) POST /interview/exit → 3) cleanup → 4) /archive
  const handleExit = async () => {
    if (isExiting) return; // 중복 클릭 방지
    setIsExiting(true);

    ttsAudioRef.current?.pause();

    if (fullRecorderRef.current?.state === "recording") {
      // 종료 경로로 설정: fullRecorder.onstop이 sendExit를 호출
      finalDestRef.current = "exit";

      // chunkRecorder: 사이클 차단 + 마지막 조각 전송 생략 (exit 시 STT 불필요)
      chunkCycleActiveRef.current = false;
      if (chunkRecorderRef.current) {
        chunkRecorderRef.current.ondataavailable = null;
        chunkRecorderRef.current.stop();
      }

      // fullRecorder onstop(sendExit 호출)이 완료될 때까지 대기 후 WebSocket 종료
      await new Promise<void>((resolve) => {
        const recorder = fullRecorderRef.current;
        if (!recorder) { resolve(); return; }
        const prev = recorder.onstop as ((e: Event) => Promise<void>) | null;
        recorder.onstop = async (e) => {
          await prev?.(e);
          resolve();
        };
        recorder.stop();
      });
    } else {
      // 녹음 중이 아닌 경우 (AI_SPEAKING 등): onstop 핸들러 제거 후 exitV2 직접 호출
      // onstop을 그대로 두면 sendNext + setStatus("WAITING_AI")가 재실행될 수 있음
      if (chunkRecorderRef.current) {
        chunkRecorderRef.current.ondataavailable = null;
      }
      if (fullRecorderRef.current) {
        fullRecorderRef.current.onstop = null;
      }

      // 음성 입력 없이 종료한 경우에도 서버에 인터뷰 종료를 알려야 데이터가 저장됨
      // answerAudio: "" / totalChunk: "0" → 서버가 오디오 없이 인터뷰를 정상 종료 처리
      const question = questionRef.current;
      if (question) {
        await exitInterviewV2({
          interviewId: question.interviewId,
          questionId: question.questionId,
          answerAudio: "",
          totalChunk: "0",
        });
      }
    }

    // WebSocket을 끊어 백엔드로부터의 메시지 수신 차단
    disconnect();

    // exitInterviewV2는 fullRecorder.onstop에서 이미 호출됨
    cleanup();
    // 인터뷰 목록 캐시를 무효화해 /archive에서 새 항목이 즉시 표시되도록 함
    await queryClient.invalidateQueries({ queryKey: ["interviewHistory"] });
    router.push("/archive");
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
    chunkRecorderRef.current?.stop();
    chunkRecorderRef.current = null;
    fullRecorderRef.current?.stop();
    fullRecorderRef.current = null;
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
    // ref를 null로 초기화하여 중복 호출 시 "Cannot close a closed AudioContext" 방지
    audioContextRef.current?.close();
    audioContextRef.current = null;
    ttsAudioRef.current?.pause();
    ttsAudioRef.current = null;
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

// Markdown 문법 제거 함수
function stripMarkdown(text: string): string {
  return text
    .replace(/#{1,6}\s*/g, "") // ## 헤더
    .replace(/\*{1,2}([^*]*)\*{1,2}/g, "$1") // **bold**, *italic*
    .replace(/_{1,2}([^_]*)_{1,2}/g, "$1") // __bold__, _italic_
    .replace(/`{1,3}[^`]*`{1,3}/g, "") // `code`, ```code```
    .replace(/^[-*>]\s+/gm, "") // 목록 (-), 인용 (>)
    .replace(/[()[\]]/g, "") // 괄호
    .trim();
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
