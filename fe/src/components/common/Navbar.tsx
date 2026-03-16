"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, useRef, useEffect } from "react";
import { logout } from "@/services/auth"; // 로그아웃 API 호출 함수
import styles from "./Navbar.module.css";

export default function Navbar() {
  // 테스트를 위한 임시 로그인 상태 (실제 구현 시 auth logic 연동)
  const [isLoggedIn, setIsLoggedIn] = useState(true);

  // 드롭다운 열림/닫힘 상태
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);

  const router = useRouter();

  // useRef: 드롭다운 DOM 요소를 직접 참조 - 외부 클릭 감지에 사용
  const dropdownRef = useRef<HTMLDivElement>(null);

  // 드롭다운 외부 클릭 시 자동으로 닫히도록 처리
  // useEffect: 컴포넌트가 마운트될 때 이벤트 리스너 등록, 언마운트 시 제거
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      // dropdownRef.current가 클릭된 영역을 포함하지 않으면 닫기
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setIsDropdownOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    // cleanup 함수: 컴포넌트가 사라질 때 리스너 제거 (메모리 누수 방지)
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  // 로그아웃 처리 함수
  const handleLogout = async () => {
    try {
      await logout(); // 서버에 로그아웃 요청 (Redis refreshToken 삭제)
    } catch {
      // 서버 오류가 나도 프론트 토큰은 반드시 삭제
    } finally {
      // localStorage에서 토큰 제거 → 이후 API 요청에 토큰이 실리지 않음
      localStorage.removeItem("accessToken");
      localStorage.removeItem("refreshToken");
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
