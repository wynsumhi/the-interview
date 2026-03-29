import Link from "next/link";
import styles from "./IntroSection.module.css";

const WAVE_HEIGHTS = [
  28, 46, 70, 108, 58, 130, 76, 94, 52, 118, 70, 88, 40, 112, 58, 76, 34, 94,
  52, 70,
];
const WAVE_COLORS = ["#c9a99a", "#9b91d4"];

export default function IntroSection() {
  return (
    <section className={styles.section}>
      <div className={`${styles.orb} ${styles.orb1}`} />
      <div className={`${styles.orb} ${styles.orb2}`} />

      <div className={styles.container}>
        <div className={styles.grid}>
          {/* 텍스트 */}
          <div>
            <div className={styles.eyebrow}>
              <span className={styles.eyebrowLine} />
              <span className={styles.eyebrowText}>Vol.01</span>
            </div>

            <h1 className={styles.h1}>The</h1>
            <div className={styles.h1Row}>
              <h1 className={styles.h1}>interview</h1>
            </div>

            <p className={styles.handwriting}>나를 발견하는 공간 ✦</p>

            <p className={styles.body}>
              AI 인터뷰어가 당신에게 진심어린 질문을 건넵니다.
              <br />
              목소리로 답하고, 나만의 매거진으로 아카이빙하세요.
            </p>

            <div className={styles.cta}>
              <Link href="/signup" className={styles.ctaPrimary}>
                인터뷰 시작하기
              </Link>
              <Link href="#why" className={styles.ctaSecondary}>
                서비스 소개 <span className={styles.ctaArrow}>↓</span>
              </Link>
            </div>
          </div>

          {/* 인터뷰 카드 */}
          <div className={styles.cardWrap}>
            <div className={styles.cardOuter}>
              <div className={styles.cardLayer1} />
              <div className={styles.cardLayer2} />

              <div className={styles.card}>
                <div className={styles.cardHeader}>
                  <span className={styles.cardHeaderLabel}>Interview</span>
                  <span className={styles.cardHeaderNum}>Q.01</span>
                </div>

                <p className={styles.cardQuestion}>
                  오늘 하루 중 가장 인상 깊었던
                  <br />
                  순간은 언제였나요?
                </p>

                <div className={styles.waveform}>
                  {WAVE_HEIGHTS.map((h, i) => (
                    <div
                      key={i}
                      className={styles.waveBar}
                      style={{
                        height: `${h}px`,
                        background: WAVE_COLORS[i % 3],
                        animationDelay: `${i * 0.075}s`,
                        opacity: 0.6 + (i % 3) * 0.13,
                      }}
                    />
                  ))}
                </div>

                <div className={styles.cardFooter}>
                  <div className={styles.recording}>
                    <span className={styles.dot} />
                    <span className={styles.recordingText}>녹음 중</span>
                  </div>
                  <span className={styles.cardTime}>02:34</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className={styles.scrollHint}>
        <span className={styles.scrollText}>Scroll</span>
        <div className={styles.scrollLine} />
      </div>
    </section>
  );
}
