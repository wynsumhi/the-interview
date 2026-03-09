// 질문 카드 컴포넌트

interface Props {
  question: string;
}

export default function QuestionCard({ question }: Props) {
  return (
    <div
      style={{
        width: "700px",
        fontSize: "28px",
        fontWeight: "600",
        textAlign: "center",
        lineHeight: "1.5",
      }}
    >
      {question}
    </div>
  );
}
