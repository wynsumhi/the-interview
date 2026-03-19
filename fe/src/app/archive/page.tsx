"use client";

import { useState, useCallback, useRef, useEffect } from "react";
import { motion, AnimatePresence, type Variants } from "framer-motion";
import { useInterviewHistory } from "@/hooks/useInterviewHistory";
import styles from "./page.module.css";
import Link from "next/link";

const TAB_HEIGHT = 42;
const VISIBLE = 5; // 슬라이딩 윈도우 크기
const COLORS = [
  styles.sage,
  styles.lavender,
  styles.blush,
  styles.charcoal,
  styles.warm,
];

// 활성 탭과 바디가 공유하는 방향성 슬라이드 variants
const slideVariants: Variants = {
  enter: (dir: number) => ({
    opacity: 0,
    y: dir > 0 ? 60 : -60,
  }),
  center: {
    opacity: 1,
    y: 0,
    transition: {
      duration: 0.45,
      ease: [0.25, 0.46, 0.45, 0.94] as [number, number, number, number],
    },
  },
  exit: (dir: number) => ({
    opacity: 0,
    y: dir > 0 ? -30 : 30,
    transition: { duration: 0.25, ease: "easeIn" },
  }),
};

// 탭·높이 변화에 공통으로 적용할 트랜지션
const SLIDE_TRANSITION = {
  duration: 0.4,
  ease: [0.25, 0.46, 0.45, 0.94] as [number, number, number, number],
};

export default function ArchivePage() {
  const { data, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading } =
    useInterviewHistory();

  const interviews = data?.pages.flatMap((p) => p.interviews) ?? [];
  console.log(
    "[ArchivePage] interviews 누적 →",
    interviews.length,
    "개 / pages 수 =",
    data?.pages.length ?? 0,
    "/ hasNextPage =",
    hasNextPage,
  );

  const [activeIndex, setActiveIndex] = useState(0);
  const [direction, setDirection] = useState(1);
  const isAnimating = useRef(false);

  // activeIndex 변화 추적
  useEffect(() => {
    console.log(
      "[ArchivePage] activeIndex =",
      activeIndex,
      "/ interviews.length =",
      interviews.length,
      "/ fetchNextPage 트리거 조건 =",
      activeIndex + VISIBLE >= interviews.length && hasNextPage,
    );
  }, [activeIndex, interviews.length, hasNextPage]);
  const didPrefetch = useRef(false);

  // 데이터 범위 보정
  useEffect(() => {
    if (interviews.length > 0 && activeIndex >= interviews.length) {
      setActiveIndex(interviews.length - 1);
    }
  }, [interviews.length, activeIndex]);

  // 첫 페이지 로드 직후 다음 페이지 선제 프리페치
  useEffect(() => {
    if (
      !didPrefetch.current &&
      interviews.length > 0 &&
      hasNextPage &&
      !isFetchingNextPage
    ) {
      didPrefetch.current = true;
      fetchNextPage();
    }
  }, [interviews.length, hasNextPage, isFetchingNextPage, fetchNextPage]);

  // 슬라이딩 윈도우: activeIndex가 항상 첫 번째 (맨 아래 활성 폴더)
  // 나머지 VISIBLE-1개는 위에 쌓이는 다음 항목들
  const windowStart = activeIndex;
  const visibleItems = interviews.slice(windowStart, windowStart + VISIBLE);

  // 비활성 탭 목록 (activeIndex 제외)
  const inactiveItems = visibleItems
    .map((item, wi) => ({ item, windowIdx: wi, globalIdx: windowStart + wi }))
    .filter(({ globalIdx }) => globalIdx !== activeIndex);

  const navigate = useCallback(
    (delta: number) => {
      if (isAnimating.current) return;
      const next = activeIndex + delta;
      if (next < 0 || next >= interviews.length) return;

      isAnimating.current = true;
      setDirection(delta > 0 ? 1 : -1);
      setActiveIndex(next);
      setTimeout(() => {
        isAnimating.current = false;
      }, 500);

      // 윈도우 끝(activeIndex + VISIBLE)이 로드된 데이터 끝에 가까워지면 프리페치
      console.log(
        "[navigate] next =", next,
        "/ interviews.length =", interviews.length,
        "/ hasNextPage =", hasNextPage,
        "/ isFetchingNextPage =", isFetchingNextPage,
        "/ 트리거 여부 =", delta > 0 && next + VISIBLE >= interviews.length && hasNextPage && !isFetchingNextPage,
      );
      if (
        delta > 0 &&
        next + VISIBLE >= interviews.length &&
        hasNextPage &&
        !isFetchingNextPage
      ) {
        console.log("[navigate] fetchNextPage() 호출");
        fetchNextPage();
      }
    },
    [
      activeIndex,
      interviews.length,
      hasNextPage,
      isFetchingNextPage,
      fetchNextPage,
    ],
  );

  const handleWheel = useCallback(
    (e: React.WheelEvent) => {
      if (e.deltaY > 0) {
        navigate(1);
      } else if (e.deltaY < 0) {
        navigate(-1);
      }
    },
    [navigate],
  );

  const activeItem = interviews[activeIndex];

  return (
    <main className={styles.container}>
      <div className={styles.contentWrapper}>
        {/* 왼쪽 섹션 */}
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
          {/*
           * folderArea: position:relative 컨테이너
           *
           * [비활성 탭 레이어]  z-index 1~VISIBLE-1
           *   tabItem이 absolute로 쌓임 (top: (len-1-wi)*TAB_HEIGHT)
           *
           * [활성 폴더 레이어]  z-index VISIBLE+1
           *   단일 motion.div (inset:0, pointer-events:none)
           *   folderTab  : 상단 자연 배치 (배경색)
           *   folderBody : absolute, top=bodyTop (배경색)
           *   사이 투명 영역으로 비활성 탭이 비쳐 보임
           *   slideVariants 하나로 탭+바디가 함께 움직임
           */}
          {/*
           * folderArea: flex-column 컨테이너
           *
           * [비활성 탭]  flex-shrink:0, 위에서부터 쌓임 (oldest → newest inactive)
           *   layout prop으로 탭 추가/제거 시 위치 변화를 부드럽게 처리
           *
           * [활성 폴더]  flex:1, 맨 아래 배치
           *   folderTab + folderBody가 직접 연결된 단일 motion.div
           *   slideVariants로 폴더 전체가 한 단위로 슬라이드
           */}
          <div className={styles.folderArea}>

            {/* 비활성 탭: flex 흐름 상단에 쌓임, layout으로 위치 변화 애니메이션 */}
            <AnimatePresence custom={direction}>
              {inactiveItems.map(({ item, globalIdx }) => (
                <motion.div
                  layout
                  key={item.interviewId}
                  className={`${styles.tabItem} ${COLORS[globalIdx % COLORS.length]}`}
                  initial={{ opacity: 0, y: direction > 0 ? -TAB_HEIGHT : TAB_HEIGHT }}
                  animate={{ opacity: 0.6, y: 0 }}
                  exit={{ opacity: 0, y: direction > 0 ? -TAB_HEIGHT : TAB_HEIGHT }}
                  transition={SLIDE_TRANSITION}
                  onClick={() => {
                    if (!isAnimating.current) {
                      setDirection(globalIdx > activeIndex ? 1 : -1);
                      setActiveIndex(globalIdx);
                    }
                  }}
                >
                  <div className={styles.folderTab}>
                    <span className={styles.code}>
                      CODE_{String(globalIdx + 1).padStart(3, "0")} //
                    </span>
                    <span className={styles.tabTitle}>
                      {item.interviewTitle.split(":")[0]}
                    </span>
                  </div>
                </motion.div>
              ))}
            </AnimatePresence>

            {/* 활성 폴더: folderTab + folderBody 직접 연결, 단일 슬라이드 단위 */}
            <AnimatePresence custom={direction}>
              {isLoading ? (
                <motion.div
                  key="loading"
                  className={`${styles.activeFolder} ${styles.emptyFolder}`}
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                >
                  <div className={styles.folderTab} />
                  <div className={styles.folderBody} />
                </motion.div>
              ) : activeItem ? (
                <motion.div
                  key={activeItem.interviewId}
                  className={`${styles.activeFolder} ${COLORS[activeIndex % COLORS.length]}`}
                  custom={direction}
                  variants={slideVariants}
                  initial="enter"
                  animate="center"
                  exit="exit"
                >
                  <div className={styles.folderTab}>
                    <span className={styles.code}>
                      CODE_{String(activeIndex + 1).padStart(3, "0")} //
                    </span>
                    <span className={styles.tabTitle}>
                      {activeItem.interviewTitle.split(":")[0]}
                    </span>
                  </div>
                  <div className={styles.folderBody}>
                    <Link
                      href={`/magazine/${activeItem.interviewId}`}
                      className={styles.bodyLink}
                    >
                      <div className={styles.bodyInner}>
                        <span className={styles.verticalDate}>
                          {new Date(activeItem.interviewDate).toLocaleDateString("ko-KR")}
                        </span>
                        <h2 className={styles.cardTitle}>
                          {activeItem.interviewTitle}
                        </h2>
                        <span className={styles.verticalDate}>
                          {String(activeIndex + 1).padStart(2, "0")}
                        </span>
                      </div>
                    </Link>
                  </div>
                </motion.div>
              ) : (
                <motion.div
                  key={`empty-${activeIndex}`}
                  className={`${styles.activeFolder} ${styles.emptyFolder}`}
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                >
                  <div className={styles.folderTab} />
                  <div className={styles.folderBody} />
                </motion.div>
              )}
            </AnimatePresence>

          </div>
        </section>
      </div>
    </main>
  );
}
