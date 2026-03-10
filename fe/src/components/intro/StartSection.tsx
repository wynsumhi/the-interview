import Link from "next/link";
import styles from "./StartSection.module.css";

export default function StartSection() {
  return (
    <section className={styles.section}>
      <div className={styles.bg} />
      <div className={styles.grain} />

      <div className={styles.inner}>
        <span className={styles.eyebrow}>지금 바로 시작해보세요</span>
        <h2 className={styles.h2}>당신의 이야기가</h2>
        <h2 className={styles.h2Handwriting}>매거진이 됩니다.</h2>

        <Link href="/signup" className={styles.btn}>
          무료로 시작하기
        </Link>

        <p className={styles.sub}>
          이미 계정이 있으신가요?{" "}
          <Link href="/login" className={styles.subLink}>
            로그인
          </Link>
        </p>
      </div>
    </section>
  );
}
