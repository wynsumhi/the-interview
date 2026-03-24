// src/app/explore/page.tsx
"use client";

import { useCallback } from "react";
import Link from "next/link";
import { useExploreInterviews } from "@/hooks/useExploreInterviews";
import { useIntersectionObserver } from "@/hooks/useIntersectionObserver";
import styles from "./page.module.css";

export default function ExplorePage() {
  const {
    data,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isLoading,
    isError,
  } = useExploreInterviews();

  const handleIntersect = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) fetchNextPage();
  }, [fetchNextPage, hasNextPage, isFetchingNextPage]);

  const sentinelRef = useIntersectionObserver(handleIntersect, {
    enabled: hasNextPage && !isFetchingNextPage,
  });

  const interviews = data?.pages.flatMap((p) => p.interviews) ?? [];

  return (
    <main className={styles.container}>
      <header className={styles.header}>
        <h1 className={styles.title}>EXPLORE</h1>
        <p className={styles.subtitle}>
          다른 이들의 문장 속에서 새로운 영감을 발견해보세요.
        </p>
      </header>

      {isLoading && <p className={styles.subtitle}>불러오는 중...</p>}
      {isError && <p className={styles.subtitle}>목록을 불러오지 못했습니다.</p>}

      <div className={styles.magazineGrid}>
        {interviews.map((item) => (
          <article key={item.interviewId} className={styles.magazineCard}>
            <Link href={`/magazine/${item.interviewId}`}>
              <div
                className={`${styles.cover} ${styles[item.fusedEmotion]}`}
              >
                <span className={styles.vol}>VOL.{item.interviewId}</span>
              </div>

              <div className={styles.content}>
                <div className={styles.meta}>
                  <span className={styles.date}>
                    {new Date(item.interviewDate).toLocaleDateString()}
                  </span>
                </div>
                <h3 className={styles.magazineTitle}>{item.interviewTitle}</h3>
                <div className={styles.footer}>
                  <span className={styles.readMore}>View Magazine →</span>
                </div>
              </div>
            </Link>
          </article>
        ))}
      </div>

      {/* 무한스크롤 sentinel */}
      <div ref={sentinelRef} />
      {isFetchingNextPage && <p className={styles.subtitle}>더 불러오는 중...</p>}
    </main>
  );
}
