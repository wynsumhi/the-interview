"use client";

import { useCallback } from "react";
import { useInterviewHistory } from "@/hooks/useInterviewHistory";
import { useIntersectionObserver } from "@/hooks/useIntersectionObserver";
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

export default function ArchivePage() {
  const {
    data,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isLoading,
    isError,
  } = useInterviewHistory();

  // 모든 페이지의 인터뷰를 하나의 배열로 펼치기
  const interviews = data?.pages.flatMap((page) => page.interviews) ?? [];

  // useEffect 의존성 안정화를 위해 메모이제이션
  const handleIntersect = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) {
      fetchNextPage();
    }
  }, [fetchNextPage, hasNextPage, isFetchingNextPage]);

  // sentinel이 뷰포트에 진입하면 handleIntersect 호출
  // 다음 페이지가 없거나 로딩 중이면 Observer를 비활성화
  const sentinelRef = useIntersectionObserver(handleIntersect, {
    enabled: hasNextPage && !isFetchingNextPage,
  });

  // 최소 5개의 슬롯을 유지 (빈 폴더로 채움)
  const totalItems = Math.max(5, interviews.length);
  const displayItems = Array.from({ length: totalItems });

  return (
    <main className={styles.container}>
      <div className={styles.contentWrapper}>
        {/* 왼쪽 섹션: 타이틀 및 인터뷰 시작 가이드 */}
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
        </section>

        {/* 오른쪽 섹션: 폴더 스택 — 스크롤 없이 고정 위치에 겹쳐 쌓임 */}
        <section className={styles.rightSection}>
          {isLoading && (
            <p className={styles.statusText}>불러오는 중...</p>
          )}
          {isError && (
            <p className={styles.statusText}>
              데이터를 불러올 수 없습니다.
            </p>
          )}
          <div
            className={styles.folderStack}
            style={
              {
                "--n": totalItems,
                "--tab-h": `${TAB_HEIGHT}px`,
              } as React.CSSProperties
            }
          >
            {displayItems.map((_, idx) => {
              const item = interviews[idx];
              const isPlaceholder = !item;
              const colorClass = isPlaceholder
                ? styles.emptyFolder
                : COLORS[idx % COLORS.length];

              const folderContent = (
                <div
                  className={`${styles.folderItem} ${colorClass}`}
                  style={{
                    // idx=0이 맨 아래(전체 보임), idx=마지막이 맨 위(탭만 보임)
                    top: `${(totalItems - 1 - idx) * TAB_HEIGHT}px`,
                    zIndex: totalItems - idx,
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
                  <div className={styles.folderBody}>
                    <div className={styles.bodyInner}>
                      {!isPlaceholder && (
                        <>
                          <span className={styles.verticalDate}>
                            {new Date(item.interviewDate).toLocaleDateString(
                              "ko-KR",
                            )}
                          </span>
                          <h2 className={styles.cardTitle}>
                            {item.interviewTitle}
                          </h2>
                        </>
                      )}
                    </div>
                  </div>
                </div>
              );

              return isPlaceholder ? (
                <div key={`empty-${idx}`}>{folderContent}</div>
              ) : (
                <Link
                  key={item.interviewId}
                  href={`/magazine/${item.interviewId}`}
                >
                  {folderContent}
                </Link>
              );
            })}
          </div>

          {/* sentinel: 이 요소가 화면에 들어오면 다음 페이지 로드 */}
          <div ref={sentinelRef} className={styles.sentinel} />
          {isFetchingNextPage && (
            <p className={styles.statusText}>더 불러오는 중...</p>
          )}
        </section>
      </div>
    </main>
  );
}
