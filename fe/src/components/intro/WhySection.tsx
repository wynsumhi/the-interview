import styles from "./WhySection.module.css";

const TAGS = ["녹음", "질문", "아카이브", "매거진", "성찰"];

export default function WhySection() {
  return (
    <section id="why" className={styles.section}>
      <div style={{ maxWidth: "var(--max-w)", margin: "0 auto" }}>

        <div className={styles.header}>
          <span className={styles.sectionNum}>01</span>
          <div className={styles.headerMeta}>
            <span className={styles.headerLabel}>방구석 인터뷰란?</span>
            <div className={styles.headerLine} />
          </div>
        </div>

        <div className={styles.grid}>
          {/* 왼쪽 */}
          <div>
            <h2 className={styles.h2}>
              AI가 진행하는
              <br />
              나만의 인터뷰
            </h2>
            <p className={styles.body}>
              방구석 인터뷰는 AI가 당신과 1:1 인터뷰를 진행하는 서비스입니다.
              매 세션마다 새로운 질문으로 당신의 생각, 감정, 경험을 탐색합니다.
            </p>
            <p className={styles.body}>
              인터뷰가 끝나면 자동으로 매거진 형태로 아카이빙되어,
              시간이 지나도 그날의 나를 다시 만날 수 있습니다.
            </p>
            <p className={styles.handwriting}>"오늘의 나를 기록한다는 것" ✦</p>
          </div>

          {/* 오른쪽 */}
          <div className={styles.quoteWrap}>
            <div className={styles.quoteBorder} />
            <div className={styles.quoteInner}>
              <p className={styles.quote}>
                "말하는 것만으로도
                <br />
                생각이 정리된다."
              </p>
              <div className={styles.tags}>
                {TAGS.map((tag) => (
                  <span key={tag} className={styles.tag}>{tag}</span>
                ))}
              </div>
              <p className={styles.quoteBody}>
                혼자이지만 혼자가 아닌 인터뷰 경험을 통해
                <br />
                나 자신과 더 깊은 대화를 나눠보세요.
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
