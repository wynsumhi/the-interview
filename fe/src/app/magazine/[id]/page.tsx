"use client";

import { useState, useRef, useEffect } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { useInterviewDetail } from "@/hooks/useInterviewDetail";
import { deleteInterview, toggleBookmark } from "@/services/interview";
import type { InterviewDetailEntry } from "@/types/interview";
import styles from "./page.module.css";

// ── 최소형 오디오 플레이어 ────────────────────────────────────
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
        const token = localStorage.getItem("accessToken"); // 토큰 키 이름 맞게 수정
        const res = await fetch(`/api/v1/interview/sound/play/${voiceUuid}`, {
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        });
        if (!res.ok) {
          setLoadError(true);
          return;
        }
        const blob = await res.blob();
        objectUrl = URL.createObjectURL(blob);
        setBlobUrl(objectUrl);
      } catch {
        setLoadError(true);
      }
    };
    fetchAudio();
    return () => {
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
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
        onTimeUpdate={() =>
          audioRef.current && setCurrentTime(audioRef.current.currentTime)
        }
        onLoadedMetadata={() =>
          audioRef.current && setDuration(audioRef.current.duration)
        }
        onEnded={() => setIsPlaying(false)}
      />
      <button
        className={styles.playBtn}
        onClick={togglePlay}
        aria-label={isPlaying ? "pause" : "play"}
      >
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
        <input
          className={styles.track}
          type="range"
          min={0}
          max={duration || 0}
          step={0.1}
          value={currentTime}
          onChange={handleSeek}
        />
      </div>
      <span className={styles.timestamp}>
        {fmt(currentTime)} / {fmt(duration)}
      </span>
    </div>
  );
}

// ── 단일 Q&A 모듈 ─────────────────────────────────────────────
function QnaModule({
  entry,
  index,
  isBookmarked,
  onToggleBookmark,
}: {
  entry: InterviewDetailEntry;
  index: number;
  isBookmarked: boolean;
  onToggleBookmark: () => void;
}) {
  const seq = String(index + 1).padStart(2, "0");
  return (
    <article className={styles.module}>
      <div className={styles.moduleInner}>
        <header className={styles.moduleHeader}>
          <span className={styles.seqLabel}>{seq}</span>
          <div className={styles.rule} />
          {/* 북마크(핀) 토글 버튼 */}
          <button
            className={`${styles.pinBtn} ${isBookmarked ? styles.pinActive : ""}`}
            onClick={onToggleBookmark}
            aria-label={isBookmarked ? "북마크 해제" : "북마크 추가"}
          >
            <svg
              width="13"
              height="16"
              viewBox="0 0 13 16"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path
                d="M1 1h11v13l-5.5-3.5L1 14V1z"
                stroke="currentColor"
                strokeWidth="1.4"
                strokeLinejoin="round"
                fill={isBookmarked ? "currentColor" : "none"}
              />
            </svg>
          </button>
        </header>
        <blockquote className={styles.question}>
          {entry.interviewQuestion}
        </blockquote>
        <div className={styles.answerBlock}>
          {entry.interviewVoice && (
            <AudioPlayer voiceUuid={entry.interviewVoice} />
          )}
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

// ── 삭제 확인 모달 ────────────────────────────────────────────
function DeleteModal({
  onConfirm,
  onCancel,
  isDeleting,
  deleteError,
}: {
  onConfirm: () => void;
  onCancel: () => void;
  isDeleting: boolean;
  deleteError: string | null;
}) {
  return (
    <div className={styles.overlay} onClick={onCancel}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <p className={styles.modalTitle}>이 인터뷰를 삭제할까요?</p>
        <p className={styles.modalDesc}>삭제된 기록은 복구할 수 없습니다.</p>
        {deleteError && <p className={styles.modalError}>{deleteError}</p>}
        <div className={styles.modalActions}>
          <button
            className={styles.cancelBtn}
            onClick={onCancel}
            disabled={isDeleting}
          >
            취소
          </button>
          <button
            className={styles.confirmBtn}
            onClick={onConfirm}
            disabled={isDeleting}
          >
            {isDeleting ? "삭제 중…" : "삭제"}
          </button>
        </div>
      </div>
    </div>
  );
}

// ── 메인 페이지 ───────────────────────────────────────────────
export default function MagazinePage() {
  const params = useParams();
  const router = useRouter();
  const interviewId = Number(params.id);
  const { data, isLoading, error } = useInterviewDetail(interviewId);

  const [showModal, setShowModal] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  // 북마크 상태 맵: answerId → true(북마크됨) / false(해제됨)
  const [bookmarks, setBookmarks] = useState<Record<number, boolean>>({});
  // 서버 데이터로 북마크 초기값이 세팅됐는지 추적 (그리드 렌더링 게이트)
  const [bookmarkReady, setBookmarkReady] = useState(false);
  // 최초 1회만 서버 값으로 초기화하기 위한 플래그 (백그라운드 리패치 시 덮어쓰기 방지)
  const bookmarkSeeded = useRef(false);

  useEffect(() => {
    if (!data?.interview || bookmarkSeeded.current) return;
    const initial: Record<number, boolean> = {};
    data.interview.forEach((entry) => {
      // 백엔드가 shareRange 필드를 추가하면 이 값으로 초기 북마크 상태가 복원됨
      initial[entry.interviewAnswerId] = entry.shareRange === true;
    });
    setBookmarks(initial);
    bookmarkSeeded.current = true;
    setBookmarkReady(true);
  }, [data]);

  const handleToggleBookmark = async (interviewAnswerId: number) => {
    // 클릭 시점의 상태를 캡처해 롤백 기준으로 사용
    const wasBookmarked = bookmarks[interviewAnswerId] === true;

    // 낙관적으로 UI 먼저 전환
    setBookmarks((prev) => ({ ...prev, [interviewAnswerId]: !wasBookmarked }));

    try {
      await toggleBookmark(interviewId, interviewAnswerId, !wasBookmarked);
    } catch {
      // API 실패 시 캡처한 상태로 정확히 복원
      setBookmarks((prev) => ({ ...prev, [interviewAnswerId]: wasBookmarked }));
    }
  };

  const handleDeleteConfirm = async () => {
    setIsDeleting(true);
    setDeleteError(null);
    try {
      await deleteInterview(interviewId);
      router.push("/archive");
    } catch (err) {
      const is400 = (err as Error).message === "BAD_REQUEST";
      setDeleteError(
        is400
          ? "삭제할 수 없는 인터뷰입니다."
          : "오류가 발생했습니다. 다시 시도해 주세요.",
      );
      setIsDeleting(false);
    }
  };

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
    return (
      <main className={styles.container}>
        <div className={styles.stateBox}>
          <span className={styles.stateCode}>
            {is403 ? "403" : is404 ? "404" : "ERR"}
          </span>
          <p className={styles.stateMsg}>
            {is403
              ? "이 인터뷰에 접근할 권한이 없습니다."
              : is404
                ? "인터뷰를 찾을 수 없습니다."
                : "불러오는 중 오류가 발생했습니다."}
          </p>
          <Link href="/archive" className={styles.backLink}>
            ← 아카이브로 돌아가기
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
      {/* ── 헤더 ── */}
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
          <div className={styles.headerActions}>
            <span className={styles.count}>{entries.length} Questions</span>
            <button
              className={styles.deleteBtn}
              onClick={() => setShowModal(true)}
            >
              Delete
            </button>
          </div>
        </div>
      </header>

      {showModal && (
        <DeleteModal
          onConfirm={handleDeleteConfirm}
          onCancel={() => {
            setShowModal(false);
            setDeleteError(null);
          }}
          isDeleting={isDeleting}
          deleteError={deleteError}
        />
      )}

      {/* ── 스태거드 2단 그리드 — 북마크 초기값이 세팅된 뒤에만 렌더링 ── */}
      <div
        className={styles.grid}
        style={bookmarkReady ? undefined : { visibility: "hidden" }}
      >
        {/* 왼쪽 열: 짝수 인덱스 (0, 2, 4 …) */}
        <div className={styles.col}>
          {leftCol.map((entry, i) => (
            <QnaModule
              key={i * 2}
              entry={entry}
              index={i * 2}
              isBookmarked={bookmarks[entry.interviewAnswerId] === true}
              onToggleBookmark={() =>
                handleToggleBookmark(entry.interviewAnswerId)
              }
            />
          ))}
        </div>
        {/* 오른쪽 열: 홀수 인덱스 (1, 3, 5 …) — 아래로 오프셋돼 스태거 효과 */}
        <div className={`${styles.col} ${styles.colOffset}`}>
          {rightCol.map((entry, i) => (
            <QnaModule
              key={i * 2 + 1}
              entry={entry}
              index={i * 2 + 1}
              isBookmarked={bookmarks[entry.interviewAnswerId] === true}
              onToggleBookmark={() =>
                handleToggleBookmark(entry.interviewAnswerId)
              }
            />
          ))}
        </div>
      </div>
    </main>
  );
}
