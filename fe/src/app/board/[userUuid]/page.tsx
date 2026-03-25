"use client";

// useState: 어떤 탭이 선택됐는지 기억할 때 사용
import { use, useState } from "react";
import { useUserStore } from "@/store/useUserStore";
import MemoCard from "@/components/board/MemoCard";
import type { QnaPreview } from "@/components/board/MemoCard";
import styles from "./page.module.css";

// 탭 종류를 타입으로 정해두어 오타 방지
type TabType = "my" | "questions";

// API layout 필드 타입 (실제 응답의 layout 객체와 동일한 구조)
interface ItemLayout {
  colorCode: string;
  rotationDeg: number;
  positionX: number; // 0–1, 캔버스 너비 대비 비율
  positionY: number; // 0–1, 캔버스 높이 대비 비율
  zIndex: number;
}

// API items 배열 한 항목의 타입
interface BoardItem {
  id: number;
  postitType: string;
  category: string;
  title: string;
  date: string;
  layout: ItemLayout;
  qnaItems: QnaPreview[];
}

// TODO: 실제 API 연결 후 제거할 임시 데이터
const MEMOS: BoardItem[] = [
  {
    id: 1,
    postitType: "QNA",
    category: "today, i accepted",
    title: "Reality.",
    date: "14 / 10 / 20",
    layout: { colorCode: "#F3C5C5", rotationDeg: -3, positionX: 0.08, positionY: 0.07, zIndex: 1 },
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
    postitType: "QNA",
    category: "a memo for self",
    title: "You are enough.",
    date: "08 / 10 / 20",
    layout: { colorCode: "#F7E9A0", rotationDeg: 4, positionX: 0.32, positionY: 0.10, zIndex: 2 },
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
    postitType: "QNA",
    category: "today, i learned",
    title: "Time is a gift.",
    date: "25 / 11 / 20",
    layout: { colorCode: "#FFB38E", rotationDeg: -2, positionX: 0.50, positionY: 0.42, zIndex: 3 },
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

  // Zustand에서 내 UUID 가져오기
  const { userUuid: myUuid } = useUserStore();

  // 현재 페이지의 주인인지 확인 (내 UUID와 주소창의 userUuid 비교)
  const isOwner = myUuid === userUuid;

  return (
    <div className={styles.page}>
      <header className={styles.stickyHeader}>
        <div className={styles.headerInner}>
          <span className={styles.headerTitle}>
            나의 핀/이거 다른 사람이 볼때 누군가의 핀
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
          {MEMOS.map(({ layout, ...rest }) => (
            <MemoCard
              key={rest.id}
              {...rest}
              colorCode={layout.colorCode}
              rotationDeg={layout.rotationDeg}
              positionX={layout.positionX}
              positionY={layout.positionY}
            />
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
