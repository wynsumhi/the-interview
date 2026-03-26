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
  /** 카드 배경색 (API: layout.colorCode) */
  colorCode: string;
  /** 카드 회전 각도, 도 단위 (API: layout.rotationDeg) */
  rotationDeg: number;
  /** 0–1 비율 → 그리드 셀 내 가로 오프셋 (API: layout.positionX) */
  positionX: number;
  /** 0–1 비율 → 그리드 셀 내 세로 오프셋 (API: layout.positionY) */
  positionY: number;
  qnaItems?: QnaPreview[];
  /** true이면 호버 시 Q&A 전환 없이 타이틀만 항상 표시 */
  disableHover?: boolean;
  /** 카드 하단에 표시할 액션 버튼 클릭 핸들러 */
  onAction?: () => void;
  /** 액션 버튼 레이블 */
  actionLabel?: string;
  /** true이면 카드 전체 투명도를 낮춰 화면에서 덜 두드러지게 표시 */
  dimmed?: boolean;
}

/** "Q. 한국어 텍스트" → { label: "Q.", text: "한국어 텍스트" } */
function splitLabel(raw: string): { label: string; text: string } {
  const dot = raw.indexOf(". ");
  if (dot === -1) return { label: "", text: raw };
  return { label: raw.slice(0, dot + 1), text: raw.slice(dot + 2) };
}

const fade = {
  initial: { opacity: 0 },
  animate: {
    opacity: 1,
    transition: { duration: 0.28, ease: "easeInOut" as const },
  },
  exit: {
    opacity: 0,
    transition: { duration: 0.18, ease: "easeInOut" as const },
  },
};

/** positionX/Y (0–1)을 ±HALF_RANGE px 오프셋으로 변환 */
const OFFSET_RANGE = 40; // 최대 오프셋 ±20px
function toOffset(ratio: number) {
  return (ratio - 0.5) * OFFSET_RANGE;
}

export default function MemoCard({
  category,
  title,
  date,
  colorCode,
  rotationDeg,
  positionX,
  positionY,
  qnaItems = [],
  disableHover = false,
  onAction,
  actionLabel,
  dimmed = false,
}: MemoProps) {
  const [hovered, setHovered] = useState(false);
  const featured = qnaItems[0] ?? null;

  const offsetX = toOffset(positionX);
  const offsetY = toOffset(positionY);

  return (
    <motion.div
      className={styles.card}
      initial={{ opacity: 0, y: offsetY + 24 }}
      animate={{ opacity: 1, x: offsetX, y: offsetY, rotate: rotationDeg }}
      onHoverStart={disableHover ? undefined : () => setHovered(true)}
      onHoverEnd={disableHover ? undefined : () => setHovered(false)}
      transition={{ type: "spring", stiffness: 240, damping: 24 }}
      style={{
        backgroundColor: colorCode,
        width: 260,
        height: 340,
      }}
    >
      <div className={styles.inner}>
        {/* ── 카테고리 레이블 (항상 표시) ── */}
        <p className={styles.category}>{category}</p>
        <hr className={styles.divider} />

        {/* ── 콘텐츠 슬롯: title ↔ Q&A 토글 ── */}
        <div className={styles.slot}>
          <AnimatePresence mode="wait" initial={false}>
            {!hovered ? (
              /* 기본: Julietta 타이틀 */
              <motion.h2 key="title" className={styles.title} {...fade}>
                {title}
              </motion.h2>
            ) : featured ? (
              /* 호버: 큐레이션 Q&A */
              <motion.div key="qa" className={styles.qaWrap} {...fade}>
                <p className={styles.question}>
                  <span className={styles.entryLabel}>
                    {splitLabel(featured.question).label}
                  </span>
                  <span className={styles.entryText}>
                    {splitLabel(featured.question).text}
                  </span>
                </p>
                <p className={styles.answer}>
                  <span className={styles.entryLabel}>
                    {splitLabel(featured.answer).label}
                  </span>
                  <span className={styles.entryText}>
                    {splitLabel(featured.answer).text}
                  </span>
                </p>
              </motion.div>
            ) : null}
          </AnimatePresence>
        </div>

        {/* ── 날짜 푸터 (항상 표시) ── */}
        <p className={styles.footer}>{date}</p>

        {/* ── 액션 버튼 (선택사항: 인터뷰 시작 등) ── */}
        {onAction && (
          <button className={styles.actionBtn} onClick={onAction}>
            {actionLabel}
          </button>
        )}
      </div>
    </motion.div>
  );
}
