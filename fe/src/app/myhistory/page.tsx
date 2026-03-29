"use client";

import Link from "next/link";
import { useCallback } from "react";
import { useInterviewHistory } from "@/hooks/useInterviewHistory";
import { useIntersectionObserver } from "@/hooks/useIntersectionObserver";
import styles from "./page.module.css";

export default function MyHistoryPage() {
  const { data, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading, isError } =
    useInterviewHistory();

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
        <h1 className={styles.title}>MY HISTORY</h1>
        <p className={styles.count}>
          {interviews.length > 0 ? `${interviews.length}개의 인터뷰` : ""}
        </p>
      </header>

      {isLoading && <p className={styles.status}>불러오는 중...</p>}
      {isError && <p className={styles.status}>목록을 불러오지 못했습니다.</p>}

      {!isLoading && !isError && interviews.length === 0 && (
        <p className={styles.empty}>
          아직 인터뷰 기록이 없습니다.{" "}
          <Link href="/interview/setup" className={styles.startLink}>
            첫 인터뷰 시작하기 →
          </Link>
        </p>
      )}

      <ul className={styles.list}>
        {interviews.map((item, index) => (
          <li key={item.interviewId} className={styles.item}>
            <Link href={`/magazine/${item.interviewId}`} className={styles.row}>
              <span className={styles.index}>
                {String(index + 1).padStart(2, "0")}
              </span>
              <span className={styles.interviewTitle}>{item.interviewTitle}</span>
              <span className={styles.date}>
                {new Date(item.interviewDate).toLocaleDateString("ko-KR", {
                  year: "numeric",
                  month: "2-digit",
                  day: "2-digit",
                })}
              </span>
            </Link>
          </li>
        ))}
      </ul>

      <div ref={sentinelRef} />
      {isFetchingNextPage && <p className={styles.status}>더 불러오는 중...</p>}
    </main>
  );
}
