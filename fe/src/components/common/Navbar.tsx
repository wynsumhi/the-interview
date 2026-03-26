"use client";

import Link from "next/link";
import Image from "next/image";
import userIcon from "@/assets/icons/user.svg";
import { useRouter, usePathname } from "next/navigation";
import { useState, useRef, useEffect } from "react";
import { useUserStore } from "@/store/useUserStore";
import { logout as logoutApi } from "@/services/auth"; // 로그아웃 API 호출 함수
import styles from "./Navbar.module.css";

export default function Navbar() {
  // 스토어에서 유저 정보와 로그인 상태, 로그아웃 액션 가져오기
  const { userUuid, isLoggedIn, clearLoginInfo } = useUserStore();

  // 드롭다운 열림/닫힘 상태
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);

  const router = useRouter();

  // 경로 변화 감지 - 페이지 이동 시 로그인 상태 재확인에 사용
  const pathname = usePathname();

  // useRef: 드롭다운 DOM 요소를 직접 참조 - 외부 클릭 감지에 사용
  const dropdownRef = useRef<HTMLDivElement>(null);

  // 드롭다운 외부 클릭 감지 로직 (유지)
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
      await logoutApi(); // 서버에 로그아웃 요청 (Redis refreshToken 삭제)
    } catch {
      // 서버 오류가 나도 프론트 토큰은 반드시 삭제
    } finally {
      // localStorage + 쿠키 토큰 모두 제거
      localStorage.removeItem("accessToken");
      localStorage.removeItem("refreshToken");
      // 미들웨어가 쿠키를 보고 보호 라우트를 차단하므로 쿠키도 삭제
      document.cookie = "accessToken=; path=/; max-age=0";
      clearLoginInfo(); // clearLoginInfo는 스토어에 정의
      setIsDropdownOpen(false);
      router.push("/login");
    }
  };

  return (
    <nav className={styles.nav}>
      <div className={styles.backdrop} />

      <div className={styles.navInner}>
        {/* 로고: 로그인 여부에 따라 목적지 변경 */}
        <Link
          href={isLoggedIn ? `/board/${userUuid}` : "/"}
          className={styles.logo}
        >
          The interview
        </Link>

        <div className={styles.links}>
          {isLoggedIn ? (
            <>
              {/* 로그인 시 메뉴 구성 */}
              <Link href={`/board/${userUuid}`} className={styles.navLink}>
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
      </div>
    </nav>
  );
}
