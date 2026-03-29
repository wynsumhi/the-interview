interface Props {
  text?: string;
}

export default function TranscriptBox({ text }: Props) {
  return (
    <div
      style={{
        width: "600px",
        minHeight: "120px",
        border: "1px solid #ddd",
        borderRadius: "12px",
        padding: "20px",
        marginTop: "20px",
        backgroundColor: "#fafafa",
        fontSize: "16px",
        lineHeight: "1.6",
      }}
    >
      {text ? (
        text
      ) : (
        <span style={{ color: "#999" }}>답변이 여기에 표시됩니다</span>
      )}
    </div>
  );
}
