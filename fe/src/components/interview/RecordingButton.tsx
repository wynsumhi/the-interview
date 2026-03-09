// 테스트를 위한 인터뷰 녹음 버튼 컴포넌트 - 추후 삭제 예정

interface Props {
  // 필요한 props가 있다면 여기에 정의
  isRecording: boolean; // 녹음 상태를 나타내는 prop
  onStart: () => void; // 녹음 시작 시 호출되는 함수
  onStop: () => void; // 녹음 중지 시 호출되는 함수
}

export default function RecordingButton({
  isRecording,
  onStart,
  onStop,
}: Props) {
  return (
    <button
      onClick={isRecording ? onStop : onStart}
      style={{
        fontSize: "18px",
        borderRadius: "10px",
        border: "none",
        color: isRecording ? "#e53935" : "#111",
        cursor: "pointer",
      }}
    >
      {isRecording ? "답변 종료" : "녹음 시작(TEST용)"}
    </button>
  );
}
