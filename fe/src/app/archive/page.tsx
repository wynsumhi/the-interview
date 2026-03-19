"use client";

import { useState, useCallback, useRef, useEffect } from "react";
import { motion, AnimatePresence, type Variants } from "framer-motion";
import { useInterviewHistory } from "@/hooks/useInterviewHistory";
import styles from "./page.module.css";
import Link from "next/link";

const TAB_HEIGHT = 42;
const COLORS = [
  styles.sage,
  styles.lavender,
  styles.blush,
  styles.charcoal,
  styles.warm,
];

// 스크롤 방향(direction)에 따라 body가 아래→위(down) 또는 위→아래(up)로 슬라이드
const bodyVariants: Variants = {
  enter: (dir: number) => ({
    opacity: 0,
    y: dir > 0 ? 60 : -60, // 아래 스크롤이면 아래서 올라옴, 위 스크롤이면 위서 내려옴
  }),
  center: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.45, ease: [0.25, 0.46, 0.45, 0.94] as [number, number, number, number] },
  },
  exit: (dir: number) => ({
    opacity: 0,
    y: dir > 0 ? -30 : 30, // enter의 반대 방향으로 퇴장
    transition: { duration: 0.25, ease: "easeIn" },
  }),
};

export default function ArchivePage() {
  const {
    data,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isLoading,
  } = useInterviewHistory();

  // 모든 페이지의 인터뷰를 단일 배열로 펼치기
  const interviews = data?.pages.flatMap((p) => p.interviews) ?? [];

  const [activeIndex, setActiveIndex] = useState(0);
  const [direction, setDirection] = useState(1); // 1=아래, -1=위

  // 애니메이션 중 휠 이벤트를 무시하기 위한 잠금 플래그
  const isAnimating = useRef(false);

  // 데이터 로드 후 activeIndex가 범위를 벗어나면 마지막 항목으로 보정
  useEffect(() => {
    if (interviews.length > 0 && activeIndex >= interviews.length) {
      setActiveIndex(interviews.length - 1);
    }
  }, [interviews.length, activeIndex]);

  // 인덱스 이동 공통 함수
  const navigate = useCallback(
    (delta: number) => {
      if (isAnimating.current) return;
      const next = activeIndex + delta;
      // 실제 데이터 범위 내에서만 이동
      if (next < 0 || next >= interviews.length) return;

      isAnimating.current = true;
      setDirection(delta);
      setActiveIndex(next);
      // 애니메이션 duration(450ms)보다 조금 길게 잠금 유지
      setTimeout(() => {
        isAnimating.current = false;
      }, 500);
    },
    [activeIndex, interviews.length],
  );

  const handleWheel = useCallback(
    (e: React.WheelEvent) => {
      if (e.deltaY > 0) {
        // 아래로 스크롤 → 다음 폴더
        navigate(1);
        // 끝에서 2개 남으면 다음 페이지 선로딩
        if (
          activeIndex >= interviews.length - 2 &&
          hasNextPage &&
          !isFetchingNextPage
        ) {
          fetchNextPage();
        }
      } else if (e.deltaY < 0) {
        // 위로 스크롤 → 이전 폴더
        navigate(-1);
      }
    },
    [navigate, activeIndex, interviews.length, hasNextPage, isFetchingNextPage, fetchNextPage],
  );

  const totalItems = Math.max(5, interviews.length);
  const activeItem = interviews[activeIndex];

  return (
    <main className={styles.container}>
      <div className={styles.contentWrapper}>
        {/* 왼쪽 섹션: 타이틀 & 가이드 */}
        <section className={styles.leftSection}>
          <h1 className={styles.mainTitle}>ARCHIVE</h1>
          <div className={styles.guideBox}>
            <p className={styles.targetName}>interview</p>
            <p className={styles.desc}>
              나와 나눈 대화의 조각들을 <br />
              이곳에서 다시 들여다보세요.
            </p>
            <Link href="/interview/setup" className={styles.startLink}>
              인터뷰 시작하기 <span>→</span>
            </Link>
          </div>

          {/* 현재 폴더 위치 카운터 */}
          {interviews.length > 0 && (
            <p className={styles.navCounter}>
              {String(activeIndex + 1).padStart(2, "0")}
              <span> / {String(interviews.length).padStart(2, "0")}</span>
            </p>
          )}
          {isFetchingNextPage && (
            <p className={styles.statusText}>불러오는 중...</p>
          )}
        </section>

        {/* 오른쪽 섹션: 커스텀 휠 네비게이션 */}
        <section className={styles.rightSection} onWheel={handleWheel}>
          {/* ── 탭 스택: 모든 폴더의 탭이 겹쳐 쌓임 ── */}
          <div
            className={styles.tabStack}
            style={{
              "--n": totalItems,
              "--tab-h": `${TAB_HEIGHT}px`,
            } as React.CSSProperties}
          >
            {Array.from({ length: totalItems }).map((_, idx) => {
              const item = interviews[idx];
              const isPlaceholder = !item;
              const isActive = idx === activeIndex;
              const colorClass = isPlaceholder
                ? styles.emptyFolder
                : COLORS[idx % COLORS.length];

              return (
                <div
                  key={item?.interviewId ?? `empty-${idx}`}
                  className={`${styles.tabItem} ${colorClass} ${isActive ? styles.activeTab : ""}`}
                  style={{
                    // idx=0이 가장 아래, idx=n-1이 가장 위
                    top: `${(totalItems - 1 - idx) * TAB_HEIGHT}px`,
                    // 활성 탭이 가장 앞으로
                    zIndex: isActive ? totalItems + 1 : totalItems - idx,
                  }}
                  onClick={() => {
                    // 실제 데이터가 있는 탭만 클릭 이동 가능
                    if (!isPlaceholder && idx !== activeIndex) {
                      setDirection(idx > activeIndex ? 1 : -1);
                      setActiveIndex(idx);
                    }
                  }}
                >
                  <div className={styles.folderTab}>
                    <span className={styles.code}>
                      CODE_{String(idx + 1).padStart(3, "0")} //
                    </span>
                    <span className={styles.tabTitle}>
                      {isPlaceholder
                        ? "EMPTY LOG"
                        : item.interviewTitle.split(":")[0]}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>

          {/* ── 활성 폴더 바디: 방향에 따라 슬라이드 인/아웃 ── */}
          <AnimatePresence mode="wait" custom={direction}>
            {isLoading ? (
              // 첫 로딩 중 스켈레톤
              <motion.div
                key="loading"
                className={`${styles.folderBody} ${styles.emptyFolder}`}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
              >
                <div className={styles.bodyInner} />
              </motion.div>
            ) : activeItem ? (
              // 실제 데이터 바디
              <motion.div
                key={activeItem.interviewId}
                className={`${styles.folderBody} ${COLORS[activeIndex % COLORS.length]}`}
                custom={direction}
                variants={bodyVariants}
                initial="enter"
                animate="center"
                exit="exit"
              >
                <Link
                  href={`/magazine/${activeItem.interviewId}`}
                  className={styles.bodyLink}
                >
                  <div className={styles.bodyInner}>
                    <span className={styles.verticalDate}>
                      {new Date(activeItem.interviewDate).toLocaleDateString(
                        "ko-KR",
                      )}
                    </span>
                    <h2 className={styles.cardTitle}>
                      {activeItem.interviewTitle}
                    </h2>
                    <span className={styles.verticalDate}>
                      {String(activeIndex + 1).padStart(2, "0")}
                    </span>
                  </div>
                </Link>
              </motion.div>
            ) : (
              // 빈 슬롯 바디
              <motion.div
                key={`empty-body-${activeIndex}`}
                className={`${styles.folderBody} ${styles.emptyFolder}`}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
              >
                <div className={styles.bodyInner} />
              </motion.div>
            )}
          </AnimatePresence>
        </section>
      </div>
    </main>
  );
}
