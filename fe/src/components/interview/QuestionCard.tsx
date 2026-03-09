// 질문 카드 컴포넌트

interface Props {
  question: string;
}

export default function QuestionCard({ question }: Props) {
  return (
    <div>
      <p>{question}</p>
    </div>
  );
}
