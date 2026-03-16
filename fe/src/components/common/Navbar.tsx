"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, useRef, useEffect } from "react";
import { logout } from "@/services/auth"; // 로그아웃 API 호출 함수
import styles from "./Navbar.module.css";

export default function Navbar() {
  // 초기값 false → useEffect에서 localStorage를 확인 후 실제 상태로 업데이트
  // false로 시작하는 이유: Next.js는 서버에서 먼저 렌더링하는데 서버에는 localStorage가 없음
  const [isLoggedIn, setIsLoggedIn] = useState(false);

  // 드롭다운 열림/닫힘 상태
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);

  const router = useRouter();

  // useRef: 드롭다운 DOM 요소를 직접 참조 - 외부 클릭 감지에 사용
  const dropdownRef = useRef<HTMLDivElement>(null);

  // 컴포넌트가 브라우저에 마운트된 시점에 localStorage의 토큰 존재 여부로 로그인 상태 결정
  // useEffect는 클라이언트(브라우저)에서만 실행 → localStorage 안전하게 접근 가능
  useEffect(() => {
    const token = localStorage.getItem("accessToken");
    setIsLoggedIn(!!token); // !!: 값이 있으면 true, 없으면 false로 변환
  }, []); // [] = 최초 마운트 시 한 번만 실행

  // 드롭다운 외부 클릭 시 자동으로 닫히도록 처리
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setIsDropdownOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  // 로그아웃 처리 함수
  const handleLogout = async () => {
    try {
      await logout(); // 서버에 로그아웃 요청 (Redis refreshToken 삭제)
    } catch {
      // 서버 오류가 나도 프론트 토큰은 반드시 삭제
    } finally {
      // localStorage + 쿠키 토큰 모두 제거
      localStorage.removeItem("accessToken");
      localStorage.removeItem("refreshToken");
      // 미들웨어가 쿠키를 보고 보호 라우트를 차단하므로 쿠키도 삭제
      document.cookie = "accessToken=; path=/; max-age=0";
      setIsLoggedIn(false);
      setIsDropdownOpen(false);
      router.push("/login");
    }
  };

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

            {/* 프로필 드롭다운 영역 */}
            {/* ref로 이 div 전체를 감시해서 외부 클릭 시 드롭다운 닫기 */}
            <div className={styles.profileWrapper} ref={dropdownRef}>
              {/* 버튼 클릭 시 드롭다운 토글 (열려있으면 닫고, 닫혀있으면 열기) */}
              <button
                className={styles.profileCircle}
                onClick={() => setIsDropdownOpen((prev) => !prev)}
                title="프로필"
              />

              {/* isDropdownOpen이 true일 때만 드롭다운 렌더링 */}
              {isDropdownOpen && (
                <div className={styles.dropdown}>
                  <Link
                    href="/mypage"
                    className={styles.dropdownItem}
                    onClick={() => setIsDropdownOpen(false)}
                  >
                    마이페이지
                  </Link>
                  <button
                    className={`${styles.dropdownItem} ${styles.logout}`}
                    onClick={handleLogout}
                  >
                    로그아웃
                  </button>
                </div>
              )}
            </div>
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
