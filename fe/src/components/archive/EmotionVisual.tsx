import type { FusedScores } from "@/types/interview";
import styles from "./EmotionVisual.module.css";

// API 필드명(snake_case)과 색상 팔레트 매핑
const EMOTION_BANDS: { key: keyof FusedScores; color: string }[] = [
  { key: "calm_score", color: "#B7D4BB" },
  { key: "tense_score", color: "#F3C5C5" },
  { key: "anxious_score", color: "#FFF3A6" },
  { key: "low_score", color: "#BCCEF6" },
  { key: "engaged_score", color: "#FBCDB6" },
];

interface Props {
  scores: FusedScores;
  // opacity: 아카이브 폴더 배경은 0.45(기본), 익스플로어 커버는 1.0(전체 채움)
  opacity?: number;
}

// 5개 감정 점수를 비율에 정확히 대응하는 하드 스톱 가로 바로 시각화
// 각 색상 구간 = 해당 점수 / 합계 × 100% — 경계 번짐 없이 명확히 구분됨
// 점수 합이 0이면 5색을 균등 분배 (빈 화면 방지)
export default function EmotionVisual({ scores, opacity = 0.45 }: Props) {
  const total = EMOTION_BANDS.reduce(
    (sum, { key }) => sum + (scores[key] ?? 0),
    0,
  );
  const safe = total > 0 ? total : 1;
  const useEqual = total === 0;

  // 같은 위치에 두 스톱을 연달아 선언 → 색상 간 경계가 완전히 날카롭게 유지됨
  // e.g. "#ABBEE7 0% 40%, #ADCBB0 40% 65%, ..."
  let cursor = 0;
  const stops = EMOTION_BANDS.map(({ key, color }) => {
    const pct = useEqual ? 20 : ((scores[key] ?? 0) / safe) * 100;
    const start = cursor;
    cursor += pct;
    return `${color} ${start.toFixed(3)}% ${cursor.toFixed(3)}%`;
  });

  const gradient = `linear-gradient(to right, ${stops.join(", ")})`;

  return (
    <div
      className={styles.wrapper}
      style={{ background: gradient, opacity }}
      aria-hidden="true"
    />
  );
}
