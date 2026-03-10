"use client";

import { MOCK_ARCHIVE_LIST } from "@/constants/mockData";
import styles from "./page.module.css";
import Link from "next/link";

const TAB_HEIGHT = 42; // folderTab 실제 높이 (padding 포함) — 렌더 결과에 따라 조정

export default function ArchivePage() {
  const dataCount = MOCK_ARCHIVE_LIST.length;
  // 최신 데이터가 가장 위(탭만 보이는 쪽)에 오도록 정렬
  const interviews = [...MOCK_ARCHIVE_LIST].reverse();

  // 최소 5개의 슬롯
  const displayItems = Array.from({ length: Math.max(5, dataCount) });
  const totalItems = displayItems.length;

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
            <Link href="/interview/start" className={styles.startLink}>
              인터뷰 시작하기 <span>→</span>
            </Link>
          </div>
        </section>

        {/* 오른쪽 섹션: 폴더 스택 — 스크롤 없이 고정 위치에 겹쳐 쌓임 */}
        <section className={styles.rightSection}>
          <div
            className={styles.folderStack}
            style={{ '--n': totalItems, '--tab-h': `${TAB_HEIGHT}px` } as React.CSSProperties}
          >
            {displayItems.map((_, idx) => {
              const item = interviews[idx];
              const isPlaceholder = !item;

              const colors = [
                styles.sage,
                styles.lavender,
                styles.blush,
                styles.charcoal,
                styles.warm,
              ];
              const colorClass = isPlaceholder
                ? styles.emptyFolder
                : colors[idx % colors.length];

              const folderContent = (
                <div
                  className={`${styles.folderItem} ${colorClass}`}
                  style={{
                    // idx=0이 맨 아래(전체 보임), idx=마지막이 맨 위(탭만 보임)
                    top: `${(totalItems - 1 - idx) * TAB_HEIGHT}px`,
                    // 아래쪽 폴더(idx=0)가 z-index 최상위 → body가 가장 앞에 보임
                    zIndex: totalItems - idx,
                  }}
                >
                  <div className={styles.folderTab}>
                    <span className={styles.code}>
                      CODE_{String(idx + 1).padStart(3, "0")} //
                    </span>
                    <span className={styles.tabTitle}>
                      {isPlaceholder ? "EMPTY LOG" : item.title.split(":")[0]}
                    </span>
                  </div>
                  <div className={styles.folderBody}>
                    <div className={styles.bodyInner}>
                      {!isPlaceholder && (
                        <>
                          <span className={styles.verticalDate}>
                            {new Date(item.created_at).toLocaleDateString()}
                          </span>
                          <h2 className={styles.cardTitle}>{item.title}</h2>
                          <span className={styles.verticalCount}>
                            Q&A {item.qna_list.length}
                          </span>
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
                  key={item.interview_id}
                  href={`/magazine/${item.interview_id}`}
                >
                  {folderContent}
                </Link>
              );
            })}
          </div>
        </section>
      </div>
    </main>
  );
}
