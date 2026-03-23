import MemoCard from "@/components/memo/MemoCard";
import type { QnaPreview } from "@/components/memo/MemoCard";

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

export default function MemoPage() {
  return (
    <main
      style={{
        position: "relative",
        width: "100vw",
        height: "100vh",
        backgroundColor: "#202020",
        overflow: "hidden",
      }}
    >
      {MEMOS.map((memo) => (
        <MemoCard key={memo.id} {...memo} />
      ))}
    </main>
  );
}
