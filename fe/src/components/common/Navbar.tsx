"use client";

import Link from "next/link";
import Image from "next/image";
import userIcon from "@/assets/icons/user.svg";
import { useRouter, usePathname } from "next/navigation";
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
  // 경로 변화 감지 - 페이지 이동 시 로그인 상태 재확인에 사용
  const pathname = usePathname();

  // useRef: 드롭다운 DOM 요소를 직접 참조 - 외부 클릭 감지에 사용
  const dropdownRef = useRef<HTMLDivElement>(null);

  // pathname이 바뀔 때마다 토큰 재확인
  // 이유: Navbar는 레이아웃에 있어서 페이지 이동 시 재마운트되지 않음
  // [] 대신 [pathname]을 쓰면 경로 변경마다 이 effect가 다시 실행됨
  useEffect(() => {
    const token = localStorage.getItem("accessToken");
    setIsLoggedIn(!!token);
  }, [pathname]); // pathname 변경 시마다 로그인 상태 동기화

  // 드롭다운 외부 클릭 시 자동으로 닫히도록 처리
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (
        dropdownRef.current &&
        !dropdownRef.current.contains(e.target as Node)
      ) {
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
      <Link href={isLoggedIn ? "/board" : "/"} className={styles.logo}>
        The interview
      </Link>

      <div className={styles.links}>
        {isLoggedIn ? (
          <>
            {/* 로그인 시 메뉴 구성 */}
            <Link href="/board" className={styles.navLink}>
              나의 핀
            </Link>
            <Link href="/explore" className={styles.navLink}>
              탐색하기
            </Link>
            <Link href="/interview/setup" className={styles.startBtn}>
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
              >
                <Image
                  src={userIcon}
                  alt="프로필"
                  width={32}
                  height={32}
                  className={styles.profileAvatar}
                />
              </button>

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
