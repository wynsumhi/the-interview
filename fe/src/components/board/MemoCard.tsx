"use client";

import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import styles from "./MemoCard.module.css";

export interface QnaPreview {
  question: string;
  answer: string;
}

interface MemoProps {
  id: number;
  category: string;
  title: string;
  date: string;
  color: string;
  rotate: number;
  top: number;
  left: number;
  qnaItems?: QnaPreview[];
}

/** "Q. 한국어 텍스트" → { label: "Q.", text: "한국어 텍스트" } */
function splitLabel(raw: string): { label: string; text: string } {
  const dot = raw.indexOf(". ");
  if (dot === -1) return { label: "", text: raw };
  return { label: raw.slice(0, dot + 1), text: raw.slice(dot + 2) };
}

const fade = {
  initial: { opacity: 0 },
  animate: { opacity: 1, transition: { duration: 0.28, ease: "easeInOut" as const } },
  exit:    { opacity: 0, transition: { duration: 0.18, ease: "easeInOut" as const } },
};

export default function MemoCard({
  category,
  title,
  date,
  color,
  rotate,
  top,
  left,
  qnaItems = [],
}: MemoProps) {
  const [hovered, setHovered] = useState(false);
  const featured = qnaItems[0] ?? null;

  return (
    <motion.div
      className={styles.card}
      initial={{ opacity: 0, y: 24 }}
      animate={{ opacity: 1, y: 0, rotate }}
      onHoverStart={() => setHovered(true)}
      onHoverEnd={() => setHovered(false)}
      transition={{ type: "spring", stiffness: 240, damping: 24 }}
      style={{ backgroundColor: color, top: `${top}px`, left: `${left}px`, width: 260, height: 340 }}
    >
      <div className={styles.inner}>
        {/* ── 카테고리 레이블 (항상 표시) ── */}
        <p className={styles.category}>{category}</p>

        {/* ── 콘텐츠 슬롯: title ↔ Q&A 토글 ── */}
        <div className={styles.slot}>
          <AnimatePresence mode="wait" initial={false}>
            {!hovered ? (
              /* 기본: Julietta 타이틀 */
              <motion.h2
                key="title"
                className={styles.title}
                {...fade}
              >
                {title}
              </motion.h2>
            ) : featured ? (
              /* 호버: 큐레이션 Q&A */
              <motion.div
                key="qa"
                className={styles.qaWrap}
                {...fade}
              >
                <p className={styles.question}>
                  <span className={styles.entryLabel}>{splitLabel(featured.question).label}</span>
                  <span className={styles.entryText}>{splitLabel(featured.question).text}</span>
                </p>
                <p className={styles.answer}>
                  <span className={styles.entryLabel}>{splitLabel(featured.answer).label}</span>
                  <span className={styles.entryText}>{splitLabel(featured.answer).text}</span>
                </p>
              </motion.div>
            ) : null}
          </AnimatePresence>
        </div>

        {/* ── 날짜 푸터 (항상 표시) ── */}
        <p className={styles.footer}>{date}</p>
      </div>
    </motion.div>
  );
}
