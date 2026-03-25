"use client";

// useState: 어떤 탭이 선택됐는지 기억할 때 사용
import { use, useEffect, useState } from "react";
import { useShareBoardInfo } from "@/hooks/useShareBoardInfo";
import { useBoardStore } from "@/store/useBoardStore";
import MemoCard from "@/components/board/MemoCard";
import type { QnaPreview } from "@/components/board/MemoCard";
import styles from "./page.module.css";

// 탭 종류를 타입으로 정해두어 오타 방지
type TabType = "my" | "questions";

// TODO: 실제 API 연결 후 제거할 임시 데이터 타입
const MEMOS: {
  id: number;
  category: string;
  title: string;
  date: string;
  color: string;
  rotate: number;
  top: number;
  left: number;
  qnaItems: QnaPreview[];
}[] = [
  {
    id: 1,
    category: "today, i accepted",
    title: "Reality.",
    date: "14 / 10 / 20",
    color: "#F3C5C5",
    rotate: -3,
    top: 80,
    left: 100,
    qnaItems: [
      {
        question: "Q. 오늘 무엇이 가장 힘들었나요?",
        answer: "A. 지금의 나와 되고 싶었던 나 사이의 간극이요.",
      },
      {
        question: "Q. 어떻게 앞으로 나아갔나요?",
        answer: "A. 한 번에 한 호흡씩 천천히요.",
      },
      {
        question: "Q. 내일로 무엇을 가져가실 건가요?",
        answer: "A. 나 자신을 향한 다정함입니다.",
      },
    ],
  },
  {
    id: 2,
    category: "a memo for self",
    title: "You are enough.",
    date: "08 / 10 / 20",
    color: "#F7E9A0",
    rotate: 4,
    top: 120,
    left: 380,
    qnaItems: [
      {
        question: "Q. 오늘 어떤 말을 듣고 싶었나요?",
        answer: "A. 당신이 최선을 다하고 있다는 걸 알고 있다는 말",
      },
      {
        question: "Q. 무엇을 내려놓으실 건가요?",
        answer: "A. 타인의 기대라는 무게입니다.",
      },
    ],
  },
  {
    id: 3,
    category: "today, i learned",
    title: "Time is a gift.",
    date: "25 / 11 / 20",
    color: "#FFB38E",
    rotate: -2,
    top: 500,
    left: 600,
    qnaItems: [
      {
        question: "Q. 무엇이 당신을 놀라게 했나요?",
        answer: "A. 고요한 순간이 얼마나 빨리 추억이 되어버리는지요.",
      },
      {
        question: "Q. 무엇을 다르게 해보고 싶나요?",
        answer: "A. 평범한 일상 속에 조금 더 머물고 싶어요.",
      },
      {
        question: "Q. 무엇에 감사하시나요?",
        answer: "A. 기다려준 사람들입니다.",
      },
    ],
  },
];

// params를 통해 URL에 있는 userUuid 가져오기 가능
export default function BoardPage({
  params,
}: {
  params: Promise<{ userUuid: string }>;
}) {
  // 비동기인 params를 React.use()로 풀어줌
  const resolvedParams = use(params);
  const userUuid = resolvedParams.userUuid;

  // 현재 선택된 탭 저장(초기 "my" 탭 활성화)
  const [activeTab, setActiveTab] = useState<TabType>("my");

  // 보드 기본 정보 조회 (isOwner, ownerNickname 등)
  const { data: boardInfo } = useShareBoardInfo(userUuid);
  const isOwner = boardInfo?.isOwner ?? false;

  const { setBoardInfo, clearBoardInfo } = useBoardStore();

  // API 응답이 도착하면 스토어에 저장, 페이지 이탈 시 초기화
  useEffect(() => {
    if (boardInfo) setBoardInfo(boardInfo);
    return () => clearBoardInfo();
  }, [boardInfo, setBoardInfo, clearBoardInfo]);

  return (
    <div className={styles.page}>
      <header className={styles.stickyHeader}>
        <div className={styles.headerInner}>
          <span className={styles.headerTitle}>
            {isOwner ? "나의 핀" : `${boardInfo?.ownerNickname ?? ""}의 핀`}
          </span>
          <span className={styles.headerCount}>{MEMOS.length} items</span>
        </div>

        {/* 탭 버튼: 클릭하면 activeTab 변경 */}
        <div className={styles.tabBar}>
          <button
            className={`${styles.tab} ${activeTab === "my" ? styles.tabActive : ""}`}
            onClick={() => setActiveTab("my")}
          >
            My Q&amp;A Memos
          </button>
          <button
            className={`${styles.tab} ${activeTab === "questions" ? styles.tabActive : ""}`}
            onClick={() => setActiveTab("questions")}
          >
            Question Memos
          </button>
        </div>
      </header>

      {/* activeTab 값에 따라 다른 캔버스 UI */}
      {activeTab === "my" ? (
        <div className={styles.canvas}>
          {MEMOS.map((memo) => (
            <MemoCard key={memo.id} {...memo} />
          ))}
        </div>
      ) : (
        <div className={styles.canvas}>
          {/* 다른 유저가 남긴 질문이 보일 영역 */}
          {!isOwner && <p className={styles.emptyMsg}>질문을 남겨보세요!</p>}
        </div>
      )}
    </div>
  );
}
