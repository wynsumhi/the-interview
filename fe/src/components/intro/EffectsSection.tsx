import styles from "./EffectsSection.module.css";

const EFFECTS = [
  {
    symbol: "✦",
    title: "말하기 훈련",
    desc: "생각을 말로 표현하는 연습을 통해 의사소통 능력과 자기 표현력이 자연스럽게 향상됩니다.",
    color: "#c9a99a",
  },
  {
    symbol: "◎",
    title: "사유 공간 제공",
    desc: "바쁜 일상에서 잠시 멈춰 나 자신과 대화하는 시간. 조용한 내면의 공간을 만들어 드립니다.",
    color: "#9b91d4",
  },
  {
    symbol: "△",
    title: "나를 알아가기",
    desc: "반복되는 인터뷰를 통해 내 생각의 패턴과 변화를 발견하고, 자기 이해의 깊이를 더합니다.",
    color: "#8a9e8c",
  },
];

export default function EffectsSection() {
  return (
    <section className={styles.section}>
      <div className={styles.orb} />

      <div className={styles.inner}>
        <span className={styles.headerLabel}>the interview의 효능</span>
        <h2 className={styles.h2}>인터뷰가 주는 것들</h2>

        <div className={styles.grid}>
          {EFFECTS.map((item) => (
            <div
              key={item.title}
              className={styles.card}
              style={{ borderColor: item.color + "33" }}
            >
              <div className={styles.symbol} style={{ color: item.color }}>
                {item.symbol}
              </div>
              <h3 className={styles.cardTitle}>{item.title}</h3>
              <p className={styles.cardDesc}>{item.desc}</p>
            </div>
          ))}
        </div>

        <div className={styles.deco}>
          <p className={styles.decoText}>
            Interview · Archive · Discovery · Reflect ·
          </p>
        </div>
      </div>
    </section>
  );
}
