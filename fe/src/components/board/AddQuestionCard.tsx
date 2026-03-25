"use client";

import { useState } from "react";
import styles from "./AddQuestionCard.module.css";

interface Props {
  /** 질문 등록 버튼 클릭 시 호출, 입력된 질문 내용을 전달 */
  onSubmit: (content: string) => void;
  /** 뮤테이션 진행 중 여부, true일 때 등록 버튼 비활성화 */
  isSubmitting: boolean;
}

// 질문 탭 캔버스의 첫 번째 자리에 고정 배치되는 추가 카드
// 기본 상태: "+" 버튼만 표시
// 편집 상태: textarea와 취소/등록 버튼 표시
export default function AddQuestionCard({ onSubmit, isSubmitting }: Props) {
  const [editing, setEditing] = useState(false);
  const [content, setContent] = useState("");

  function handleSubmit() {
    const trimmed = content.trim();
    if (!trimmed) return;
    onSubmit(trimmed);
    // 낙관적 초기화: 응답 대기 중에도 입력창을 닫아 UX를 빠르게 유지
    setContent("");
    setEditing(false);
  }

  function handleCancel() {
    setContent("");
    setEditing(false);
  }

  return (
    <div className={styles.card}>
      <div className={styles.inner}>
        {/* 카테고리 레이블: MemoCard와 동일한 위치/스타일 */}
        <p className={styles.category}>Question</p>
        <hr className={styles.divider} />

        {/* 콘텐츠 슬롯: 기본(타이틀 + "+" 버튼) ↔ 편집(textarea) 전환 */}
        <div className={styles.slot}>
          {!editing ? (
            <div className={styles.promptGroup}>
              <p className={styles.promptTitle}>
                Click here to add<br />an interview question!
              </p>
              <button
                className={styles.addButton}
                onClick={() => setEditing(true)}
                aria-label="새 질문 추가"
              >
                +
              </button>
            </div>
          ) : (
            <textarea
              className={styles.textarea}
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="질문을 입력하세요"
              maxLength={200}
              // eslint-disable-next-line jsx-a11y/no-autofocus
              autoFocus
            />
          )}
        </div>

        {/* 편집 상태일 때만 취소/등록 버튼 노출 */}
        {editing && (
          <div className={styles.actions}>
            <button className={styles.cancelBtn} onClick={handleCancel}>
              취소
            </button>
            <button
              className={styles.submitBtn}
              onClick={handleSubmit}
              disabled={isSubmitting || !content.trim()}
            >
              {isSubmitting ? "..." : "등록"}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
