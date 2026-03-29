"use client";

import { useState, useRef, useEffect } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { fetchSharedInterviewDetail } from "@/services/interview";
import type { InterviewDetailEntry } from "@/types/interview";
import styles from "../../[id]/page.module.css";

// ── 최소형 오디오 플레이어 (magazine/[id]/page.tsx와 동일) ─────
function AudioPlayer({ voiceUuid }: { voiceUuid: string }) {
  const audioRef = useRef<HTMLAudioElement>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [loadError, setLoadError] = useState(false);

  useEffect(() => {
    let objectUrl: string;
    const fetchAudio = async () => {
      try {
        const token = localStorage.getItem("accessToken");
        const res = await fetch(`/api/v1/interview/sound/play/${voiceUuid}`, {
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        });
        if (!res.ok) { setLoadError(true); return; }
        const blob = await res.blob();
        objectUrl = URL.createObjectURL(blob);
        setBlobUrl(objectUrl);
      } catch {
        setLoadError(true);
      }
    };
    fetchAudio();
    return () => { if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [voiceUuid]);

  if (loadError) return null;

  const togglePlay = () => {
    if (!audioRef.current) return;
    isPlaying ? audioRef.current.pause() : audioRef.current.play();
    setIsPlaying((p) => !p);
  };

  const handleSeek = (e: React.ChangeEvent<HTMLInputElement>) => {
    const t = Number(e.target.value);
    if (audioRef.current) audioRef.current.currentTime = t;
    setCurrentTime(t);
  };

  const fmt = (s: number) =>
    `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, "0")}`;

  return (
    <div className={styles.player}>
      <audio
        ref={audioRef}
        src={blobUrl ?? undefined}
        onTimeUpdate={() => audioRef.current && setCurrentTime(audioRef.current.currentTime)}
        onLoadedMetadata={() => audioRef.current && setDuration(audioRef.current.duration)}
        onEnded={() => setIsPlaying(false)}
      />
      <button className={styles.playBtn} onClick={togglePlay} aria-label={isPlaying ? "pause" : "play"}>
        {isPlaying ? (
          <svg width="12" height="12" viewBox="0 0 12 12" fill="currentColor">
            <rect x="1" y="1" width="3.5" height="10" rx="1" />
            <rect x="7.5" y="1" width="3.5" height="10" rx="1" />
          </svg>
        ) : (
          <svg width="12" height="12" viewBox="0 0 12 12" fill="currentColor">
            <path d="M2 1v10l9-5-9-5z" />
          </svg>
        )}
      </button>
      <div className={styles.trackWrap}>
        <input className={styles.track} type="range" min={0} max={duration || 0} step={0.1} value={currentTime} onChange={handleSeek} />
      </div>
      <span className={styles.timestamp}>{fmt(currentTime)} / {fmt(duration)}</span>
    </div>
  );
}

// ── 단일 Q&A 모듈 (북마크 없는 읽기 전용) ─────────────────────
function QnaModule({ entry, index }: { entry: InterviewDetailEntry; index: number }) {
  const seq = String(index + 1).padStart(2, "0");
  return (
    <article className={styles.module}>
      <div className={styles.moduleInner}>
        <header className={styles.moduleHeader}>
          <span className={styles.seqLabel}>{seq}</span>
          <div className={styles.rule} />
        </header>
        <blockquote className={styles.question}>{entry.interviewQuestion}</blockquote>
        <div className={styles.answerBlock}>
          {entry.interviewVoice && <AudioPlayer voiceUuid={entry.interviewVoice} />}
          {entry.interviewText ? (
            <p className={styles.answerText}>{entry.interviewText}</p>
          ) : (
            <p className={styles.answerEmpty}>— 답변이 기록되지 않았습니다</p>
          )}
        </div>
      </div>
    </article>
  );
}

// ── 메인 페이지 ───────────────────────────────────────────────
export default function SharedMagazinePage() {
  const params = useParams();
  const rawUuid = Array.isArray(params.uuid) ? params.uuid[0] : params.uuid;
  const shareUuid = rawUuid ?? "";

  const { data, isLoading, error } = useQuery({
    queryKey: ["sharedInterviewDetail", shareUuid],
    queryFn: () => fetchSharedInterviewDetail(shareUuid),
    enabled: !!shareUuid,
    retry: false,
  });

  if (isLoading) {
    return (
      <main className={styles.container}>
        <div className={styles.stateBox}>
          <span className={styles.stateCode}>—</span>
          <p className={styles.stateMsg}>불러오는 중...</p>
        </div>
      </main>
    );
  }

  if (error) {
    const msg = (error as Error).message;
    const is403 = msg === "FORBIDDEN";
    const is404 = msg === "NOT_FOUND";
    const is502 = msg === "BAD_GATEWAY";
    return (
      <main className={styles.container}>
        <div className={styles.stateBox}>
          <span className={styles.stateCode}>
            {is403 ? "403" : is404 ? "404" : is502 ? "502" : "ERR"}
          </span>
          <p className={styles.stateMsg}>
            {is403
              ? "이 인터뷰에 접근할 권한이 없습니다."
              : is502
                ? "서버에 일시적인 문제가 발생했습니다. 잠시 후 다시 시도해 주세요."
                : is404
                  ? "인터뷰를 찾을 수 없습니다."
                  : "불러오는 중 오류가 발생했습니다."}
          </p>
          <Link href="/explore" className={styles.backLink}>
            ← 탐색으로 돌아가기
          </Link>
        </div>
      </main>
    );
  }

  if (!data) return null;

  const entries = data.interview ?? [];
  const leftCol = entries.filter((_, i) => i % 2 === 0);
  const rightCol = entries.filter((_, i) => i % 2 === 1);

  return (
    <main className={styles.container}>
      <header className={styles.header}>
        <div className={styles.headerMeta}>
          <span className={styles.label}>INTERVIEW LOG</span>
          <span className={styles.idStamp}>
            {String(data.interviewId).padStart(3, "0")}
          </span>
        </div>
        <h1 className={styles.title}>{data.interviewTitle}</h1>
        <div className={styles.headerFoot}>
          <span className={styles.date}>
            {new Date(data.interviewDate).toLocaleDateString("ko-KR", {
              year: "numeric",
              month: "long",
              day: "numeric",
            })}
          </span>
          <span className={styles.count}>{entries.length} Questions</span>
        </div>
      </header>

      <div className={styles.grid}>
        <div className={styles.col}>
          {leftCol.map((entry, i) => (
            <QnaModule key={i * 2} entry={entry} index={i * 2} />
          ))}
        </div>
        <div className={styles.col}>
          {rightCol.map((entry, i) => (
            <QnaModule key={i * 2 + 1} entry={entry} index={i * 2 + 1} />
          ))}
        </div>
      </div>
    </main>
  );
}
