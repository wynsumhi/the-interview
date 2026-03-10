// src/app/magazine/[id]/page.tsx
"use client";

import { MOCK_INTERVIEW_DETAIL } from "@/constants/mockData";
import styles from "./page.module.css";
import { useParams } from "next/navigation";

export default function MagazinePage() {
  const data = MOCK_INTERVIEW_DETAIL; //

  return (
    <main className={styles.container}>
      <header className={styles.header}>
        <span className={styles.label}>
          INTERVIEW LOG // {data.interview_id}
        </span>
        <h1 className={styles.title}>{data.title}</h1>
        <div className={styles.info}>
          <span>DATE: {new Date(data.created_at).toLocaleDateString()}</span>
          <span>SHARE: {data.share_range}</span>
        </div>
      </header>

      <section className={styles.reportSection}>
        <h2 className={styles.sectionTitle}>Summary Report</h2>
        <p className={styles.reportText}>{data.report?.summary}</p>
      </section>

      <section className={styles.qnaList}>
        {data.qna_list.map((qna) => (
          <div key={qna.question_id} className={styles.qnaItem}>
            <div className={styles.questionBox}>
              <span className={styles.qNum}>Q{qna.sequence}.</span>
              <p className={styles.qText}>{qna.question_text}</p>
            </div>
            <div className={styles.answerBox}>
              <p className={styles.aText}>{qna.answer?.answer_text}</p>
            </div>
          </div>
        ))}
      </section>
    </main>
  );
}
