"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react"; // 실제로는 전역 상태(Zustand 등)나 세션에서 가져옴
import styles from "./Navbar.module.css";

export default function Navbar() {
  // 테스트를 위한 임시 로그인 상태 (실제 구현 시 auth logic 연동)
  const [isLoggedIn, setIsLoggedIn] = useState(true);
  const pathname = usePathname();
  const hideStartBtn = pathname === "/" || pathname === "/archive";

  return (
    <nav className={styles.nav}>
      <div className={styles.backdrop} />

      {/* 로고: 로그인 여부에 따라 목적지 변경 */}
      <Link href={isLoggedIn ? "/archive" : "/"} className={styles.logo}>
        The interview
      </Link>

      <div className={styles.links}>
        {isLoggedIn ? (
          <>
            {/* 로그인 시 메뉴 구성 */}
            <Link href="/archive" className={styles.navLink}>
              아카이브
            </Link>
            <Link href="/emotion" className={styles.navLink}>
              감정 인사이트
            </Link>
            <Link href="/explore" className={styles.navLink}>
              인터뷰 둘러보기
            </Link>
            <Link href="/interview/start" className={styles.startBtn}>
              인터뷰 시작
            </Link>
            <Link
              href="/mypage"
              className={styles.profileCircle}
              title="마이페이지"
            />
          </>
        ) : (
          <>
            {/* 비로그인 시 메뉴 구성 */}
            <Link href="/login" className={styles.login}>
              로그인
            </Link>
            <Link href="/signup" className={styles.signup}>
              회원가입
            </Link>
          </>
        )}
      </div>
    </nav>
  );
}
