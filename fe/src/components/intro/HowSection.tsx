import styles from "./HowSection.module.css";

const STEPS = [
  {
    num: "01",
    title: "시간 설정",
    desc: "5분 단위로 최대 30분까지\n인터뷰 시간을 설정합니다.",
    color: "#c9a99a",
  },
  {
    num: "02",
    title: "AI 질문",
    desc: "AI 인터뷰어가 음성으로\n질문을 읽어드립니다.",
    color: "#9b91d4",
  },
  {
    num: "03",
    title: "녹음 & 답변",
    desc: "질문이 끝나면 녹음이 시작됩니다.\n편하게 이야기하고 버튼으로 종료.",
    color: "#8a9e8c",
  },
  {
    num: "04",
    title: "매거진 아카이빙",
    desc: "인터뷰가 끝나면 자동으로\n나만의 매거진으로 저장됩니다.",
    color: "#c9a99a",
  },
];

export default function HowSection() {
  return (
    <section id="how" className={styles.section}>
      <div className={styles.inner}>

        <div className={styles.header}>
          <span className={styles.sectionNum}>02</span>
          <div className={styles.headerMeta}>
            <span className={styles.headerLabel}>어떻게 진행되나요?</span>
            <div className={styles.headerLine} />
          </div>
        </div>

        <div className={styles.grid}>
          {STEPS.map((step) => (
            <div key={step.num} className={styles.step}>
              <div
                className={styles.stepLine}
                style={{ background: step.color }}
              />
              <p className={styles.stepNum}>{step.num}</p>
              <h3 className={styles.stepTitle}>{step.title}</h3>
              <p className={styles.stepDesc}>{step.desc}</p>
            </div>
          ))}
        </div>

        <div className={styles.divider}>
          <div className={styles.dividerLine} />
          <p className={styles.dividerText}>질문은 매 인터뷰마다 새롭게 생성됩니다</p>
          <div className={styles.dividerLine} />
        </div>
      </div>
    </section>
  );
}
