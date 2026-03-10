// src/app/emotion/page.tsx
"use client";

import { useState } from "react";
import { MOCK_ARCHIVE_LIST } from "@/constants/mockData";
import styles from "./page.module.css";
import { EmotionType } from "@/types/interview";

export default function EmotionDashboard() {
  const [selectedType, setSelectedType] = useState<EmotionType | "all">("all");

  // 모든 인터뷰에서 질문-답변-감정 한 세트를 평탄화하여 가져옴
  const allPieces = MOCK_ARCHIVE_LIST.flatMap((interview) =>
    interview.qna_list.map((qna) => ({
      ...qna,
      interviewTitle: interview.title,
      interviewId: interview.interview_id,
    })),
  );

  // 선택된 감정에 따라 필터링
  const filteredPieces =
    selectedType === "all"
      ? allPieces
      : allPieces.filter((p) =>
          p.emotions.some((e) => e.emotion_type === selectedType),
        );

  const emotionTypes: { type: EmotionType | "all"; label: string }[] = [
    { type: "all", label: "ALL" },
    { type: "joy", label: "기쁨" },
    { type: "serenity", label: "평온" },
    { type: "insight", label: "통찰" },
    { type: "sadness", label: "슬픔" },
    { type: "anger", label: "분노" },
  ];

  return (
    <main className={styles.container}>
      <header className={styles.header}>
        <h1 className={styles.title}>EMOTION INDEX</h1>
        <p className={styles.subtitle}>
          기록된 대화 속 마음의 조각들을 분류했습니다.
        </p>
      </header>

      {/* 감정 인덱스 탭 (캐비닛 인덱스 느낌) */}
      <nav className={styles.tabNav}>
        {emotionTypes.map((emo) => (
          <button
            key={emo.type}
            onClick={() => setSelectedType(emo.type)}
            className={`${styles.tabItem} ${styles[emo.type]} ${selectedType === emo.type ? styles.active : ""}`}
          >
            {emo.label}
          </button>
        ))}
      </nav>

      {/* 감정 조각 보드 (포스트잇/인덱스 카드 느낌) */}
      <div className={styles.board}>
        {filteredPieces.map((piece, idx) => (
          <div
            key={`${piece.interviewId}-${idx}`}
            className={`${styles.pieceCard} ${styles[piece.emotions[0].emotion_type]}`}
            style={{ transform: `rotate(${((idx % 3) - 1) * 2}deg)` }}
          >
            <div className={styles.pieceHeader}>
              <span className={styles.pDate}>{piece.interviewTitle}</span>
            </div>
            <p className={styles.pQuestion}>"{piece.question_text}"</p>
            <div className={styles.pieceFooter}>
              <span className={styles.pLabel}>
                #{piece.emotions[0].emotion_label}
              </span>
              <span className={styles.pRate}>
                {piece.emotions[0].emotion_rate}%
              </span>
            </div>
          </div>
        ))}
      </div>
    </main>
  );
}
