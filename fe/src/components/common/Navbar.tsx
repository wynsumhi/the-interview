import Link from "next/link";
import styles from "./Navbar.module.css";

export default function Navbar() {
  return (
    <nav className={styles.nav}>
      <div className={styles.backdrop} />

      <Link href="/" className={styles.logo}>
        방구석 인터뷰
      </Link>

      <div className={styles.links}>
        <Link href="/login" className={styles.login}>
          로그인
        </Link>
        <Link href="/signup" className={styles.signup}>
          회원가입
        </Link>
      </div>
    </nav>
  );
}
