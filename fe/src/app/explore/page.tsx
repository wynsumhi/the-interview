// src/app/explore/page.tsx
"use client";

import { MOCK_ARCHIVE_LIST } from "@/constants/mockData";
import styles from "./page.module.css";
import Link from "next/link";

export default function ExplorePage() {
  // 실제로는 공개(PUBLIC) 설정된 인터뷰만 가져오는 API를 사용합니다.
  const publicInterviews = MOCK_ARCHIVE_LIST.filter(
    (it) => it.share_range === "PUBLIC" || it.share_range === "PRIVATE",
  );

  return (
    <main className={styles.container}>
      <header className={styles.header}>
        <h1 className={styles.title}>EXPLORE</h1>
        <p className={styles.subtitle}>
          다른 이들의 문장 속에서 새로운 영감을 발견해보세요.
        </p>
      </header>

      <div className={styles.magazineGrid}>
        {publicInterviews.map((item) => (
          <article key={item.interview_id} className={styles.magazineCard}>
            <Link href={`/magazine/${item.interview_id}`}>
              {/* 매거진 커버 이미지 역할을 하는 컬러 블록 */}
              <div
                className={`${styles.cover} ${styles[item.qna_list[0].emotions[0].emotion_type]}`}
              >
                <span className={styles.vol}>VOL.{item.interview_id}</span>
              </div>

              <div className={styles.content}>
                <div className={styles.meta}>
                  <span className={styles.author}>@{item.member_id}</span>
                  <span className={styles.date}>
                    {new Date(item.created_at).toLocaleDateString()}
                  </span>
                </div>
                <h3 className={styles.magazineTitle}>{item.title}</h3>
                <p className={styles.preview}>
                  {item.qna_list[0].answer?.answer_text.substring(0, 60)}...
                </p>
                <div className={styles.footer}>
                  <span className={styles.qCount}>
                    {item.qna_list.length} Questions
                  </span>
                  <span className={styles.readMore}>View Magazine →</span>
                </div>
              </div>
            </Link>
          </article>
        ))}
      </div>
    </main>
  );
}
