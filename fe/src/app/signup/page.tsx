// src/app/signup/page.tsx
"use client"; // Next.js에서 브라우저에서만 동작하는 코드임을 선언 (useState, 이벤트 처리 등)

import { useState } from "react"; // 컴포넌트 안에서 변하는 값을 관리하는 React 훅
import Link from "next/link";
import { useRouter } from "next/navigation"; // 페이지 이동을 위한 Next.js 훅
import styles from "./page.module.css";
import { signup, checkEmailExists } from "@/services/auth"; // 회원가입, 이메일 중복 검사 API 함수

// 비밀번호 유효성 검사 정규식
// (?=.*[a-zA-Z]) : 영문 포함 필수
// (?=.*\d)       : 숫자 포함 필수
// (?=.*[!@#$%])  : 특수문자(!@#$%) 포함 필수
// {8,12}         : 전체 길이 8~12자
const PASSWORD_REGEX =
  /^(?=.*[a-zA-Z])(?=.*\d)(?=.*[!@#$%])[a-zA-Z\d!@#$%]{8,12}$/;

export default function SignupPage() {
  const router = useRouter();

  // 각 입력 필드 값을 state로 관리
  // useState("") : 초기값이 빈 문자열인 상태 변수 선언
  // setXxx : 해당 값을 업데이트하는 함수 (호출하면 화면 자동 재렌더링)
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [nickname, setNickname] = useState("");

  // 에러 메시지 상태 (각 필드별 + 서버 에러)
  const [errors, setErrors] = useState({
    email: "",
    password: "",
    confirmPassword: "",
    general: "", // 서버에서 온 에러 메시지
  });

  // API 요청 중 여부 (true면 버튼 비활성화해서 중복 제출 방지)
  const [isLoading, setIsLoading] = useState(false);

  // 폼 제출 전 입력값이 올바른지 확인하는 함수
  const validate = (): boolean => {
    const newErrors = {
      email: "",
      password: "",
      confirmPassword: "",
      general: "",
    };
    let isValid = true;

    // 비밀번호 형식 검사
    if (!PASSWORD_REGEX.test(password)) {
      newErrors.password =
        "영문, 숫자, 특수문자(!@#$%) 포함 8~12자여야 합니다.";
      isValid = false;
    }

    // 비밀번호 일치 검사
    if (password !== confirmPassword) {
      newErrors.confirmPassword = "비밀번호가 일치하지 않습니다.";
      isValid = false;
    }

    setErrors(newErrors); // 에러 메시지를 state에 저장 → 화면에 표시됨
    return isValid;
  };

  // 회원가입 버튼 클릭 시 실행되는 핸들러
  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    // e.preventDefault() : 폼의 기본 동작(페이지 새로고침)을 막음
    e.preventDefault();

    if (!validate()) return; // 유효성 검사 실패 시 API 호출하지 않고 종료

    setIsLoading(true);

    try {
      // confirmPassword는 서버에 보내지 않음 (클라이언트 전용 확인 필드)
      await signup({ email, password, nickname });

      // 성공 시 로그인 페이지로 이동
      router.push("/login");
    } catch (error: any) {
      // 400 에러 시 이메일/닉네임 중복 여부를 구분해서 표시
      if (error.response?.status === 400) {
        const emailExists = await checkEmailExists(email);
        if (emailExists) {
          setErrors((prev) => ({
            ...prev,
            email: "이미 사용 중인 이메일입니다.",
          }));
        } else {
          setErrors((prev) => ({
            ...prev,
            general: "이미 사용 중인 닉네임입니다.",
          }));
        }
      } else {
        setErrors((prev) => ({
          ...prev,
          general: "회원가입에 실패했습니다. 다시 시도해주세요.",
        }));
      }
    } finally {
      // try/catch 결과와 상관없이 항상 실행 → 로딩 상태 해제
      setIsLoading(false);
    }
  };

  return (
    <main className={styles.container}>
      <div className={styles.authBox}>
        <Link href="/" className={styles.logo}>
          방구석 인터뷰
        </Link>

        <h2 className={styles.title}>Create Account</h2>

        {/* onSubmit: 버튼 클릭 또는 엔터 시 handleSubmit 실행 */}
        <form className={styles.form} onSubmit={handleSubmit}>
          <div className={styles.inputGroup}>
            <label>이메일</label>
            <input
              type="email"
              placeholder="example@email.com"
              className={styles.input}
              value={email}
              onChange={(e) => setEmail(e.target.value)} // 입력할 때마다 state 업데이트
              required
            />
            {errors.email && (
              <span className={styles.error}>{errors.email}</span>
            )}
          </div>

          <div className={styles.inputGroup}>
            <label>비밀번호</label>
            <input
              type="password"
              placeholder="8자 이상 입력해주세요"
              className={styles.input}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
            <span className={styles.validation}>
              * 영문, 숫자, 특수문자 조합 8자 이상
            </span>
            {/* errors.password가 있을 때만 에러 메시지 표시 */}
            {errors.password && (
              <span className={styles.error}>{errors.password}</span>
            )}
          </div>

          <div className={styles.inputGroup}>
            <label>비밀번호 확인</label>
            <input
              type="password"
              placeholder="비밀번호를 한번 더 입력해주세요"
              className={styles.input}
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              required
            />
            {errors.confirmPassword && (
              <span className={styles.error}>{errors.confirmPassword}</span>
            )}
          </div>

          <div className={styles.inputGroup}>
            <label>닉네임 (별칭)</label>
            <input
              type="text"
              placeholder="나를 표현할 이름을 정해주세요"
              className={styles.input}
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              required
            />
          </div>

          <div className={styles.submitGroup}>
            {/* 서버에서 온 에러 메시지 (이메일 중복 등) */}
            {errors.general && (
              <span className={styles.error}>{errors.general}</span>
            )}

            {/* disabled: isLoading이 true면 버튼 비활성화 */}
            <button
              type="submit"
              className={styles.submitBtn}
              disabled={isLoading}
            >
              {isLoading ? "가입 중..." : "회원가입 하기"}
            </button>
          </div>
        </form>

        <p className={styles.switchAuth}>
          이미 계정이 있으신가요? <Link href="/login">로그인</Link>
        </p>
      </div>
    </main>
  );
}
