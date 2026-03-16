// src/app/login/page.tsx
// 로그인 페이지 - 이메일/비밀번호 입력 → API 호출 → 토큰 저장 → 홈으로 이동
"use client"; // Next.js에서 useState 같은 React 훅을 쓰려면 반드시 필요

import Link from "next/link";
import { useRouter } from "next/navigation"; // 로그인 성공 후 페이지 이동에 사용
import { useState } from "react"; // 입력값·에러·로딩 상태를 관리하기 위해 사용
import { login } from "@/services/auth"; // 로그인 API 호출 함수
import styles from "./page.module.css";

export default function LoginPage() {
  const router = useRouter();

  // 폼 입력값 상태 - 사용자가 타이핑할 때마다 업데이트됨
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  // 에러 메시지 상태 - 로그인 실패 시 사용자에게 표시
  const [error, setError] = useState("");

  // 로딩 상태 - API 요청 중 버튼 중복 클릭 방지
  const [isLoading, setIsLoading] = useState(false);

  // 폼 제출 핸들러 - 로그인 버튼 클릭 또는 엔터키 입력 시 실행
  // e의 타입은 onSubmit에 연결되면 TypeScript가 자동으로 추론함
  const handleSubmit = async (e: { preventDefault: () => void }) => {
    e.preventDefault(); // 브라우저 기본 폼 제출(페이지 새로고침) 차단
    setError(""); // 이전 에러 메시지 초기화
    setIsLoading(true); // 버튼 비활성화 시작

    try {
      // 로그인 API 호출 - 성공하면 accessToken, refreshToken 반환
      const { accessToken, refreshToken } = await login({ email, password });

      // localStorage: 브라우저에 데이터를 저장하는 공간 (탭을 닫아도 유지됨)
      // accessToken은 API 요청 시 헤더에 담아 보내는 인증 토큰
      localStorage.setItem("accessToken", accessToken);
      localStorage.setItem("refreshToken", refreshToken);

      // Next.js 미들웨어는 서버에서 실행되어 localStorage를 못 읽음
      // 쿠키는 서버/클라이언트 양쪽에서 읽을 수 있어서 미들웨어 인증 체크에 사용
      // path=/: 모든 경로에서 쿠키 접근 가능 / max-age: 쿠키 유효 시간(초)
      document.cookie = `accessToken=${accessToken}; path=/; max-age=${60 * 60 * 24}`;

      // 로그인 성공 → 홈으로 이동
      router.push("/");
    } catch (err: unknown) {
      // API 에러 응답 처리 - axios 에러는 response.data.message에 서버 메시지가 담김
      const axiosError = err as {
        response?: { data?: { message?: string } };
      };
      setError(
        axiosError.response?.data?.message ?? "이메일 또는 비밀번호를 확인해주세요."
      );
    } finally {
      setIsLoading(false); // 성공/실패 상관없이 버튼 다시 활성화
    }
  };

  return (
    <main className={styles.container}>
      <div className={styles.authBox}>
        {/* 로고 (메인페이지 바로가기) */}
        <Link href="/" className={styles.logo}>
          방구석 인터뷰
        </Link>

        <h2 className={styles.title}>Welcome Back</h2>

        {/* onSubmit: 폼 제출 이벤트를 handleSubmit 함수에 연결 */}
        <form className={styles.form} onSubmit={handleSubmit}>
          <div className={styles.inputGroup}>
            <label>이메일</label>
            <input
              type="email"
              placeholder="email@example.com"
              className={styles.input}
              value={email}
              onChange={(e) => setEmail(e.target.value)} // 타이핑마다 state 업데이트
              required
            />
          </div>
          <div className={styles.inputGroup}>
            <label>비밀번호</label>
            <input
              type="password"
              placeholder="••••••••"
              className={styles.input}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>

          {/* 에러 메시지 - error 값이 있을 때만 렌더링 */}
          {error && <span className={styles.error}>{error}</span>}

          {/* disabled: isLoading이 true면 버튼 비활성화 */}
          <button type="submit" className={styles.submitBtn} disabled={isLoading}>
            {isLoading ? "로그인 중..." : "로그인"}
          </button>
        </form>

        <div className={styles.divider}>
          <span>또는</span>
        </div>

        {/* 소셜 로그인 영역 */}
        <div className={styles.socialGroup}>
          <button className={`${styles.socialBtn} ${styles.kakao}`}>
            카카오 로그인
          </button>
          <button className={`${styles.socialBtn} ${styles.naver}`}>
            네이버 로그인
          </button>
          <button className={`${styles.socialBtn} ${styles.google}`}>
            구글 로그인
          </button>
        </div>

        <p className={styles.switchAuth}>
          아직 계정이 없으신가요? <Link href="/signup">회원가입</Link>
        </p>
      </div>
    </main>
  );
}
