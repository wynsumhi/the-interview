import axios from "axios";

// ── 서버 연결 전환 방법 ──────────────────────────────────────────────────────
// 로컬 테스트:    아래 "http://localhost:8080/api/v1" 줄의 주석을 해제하고
//                "/api/v1" 줄을 주석 처리
// 프로덕션 배포:  "/api/v1" 줄을 활성화하고 localhost 줄을 주석 처리
//                (Next.js rewrites가 API_URL 환경변수 경유로 j14a307.p.ssafy.io 로 프록시)
// ✅ 현재 상태: 원격 프로덕션 서버 (j14a307.p.ssafy.io) 에 연결됨
// ─────────────────────────────────────────────────────────────────────────────
const instance = axios.create({
  // baseURL: "http://localhost:8080/api/v1", // 로컬 테스트용
  baseURL: "/api/v1",                         // 프로덕션 (Next.js 프록시 → j14a307.p.ssafy.io)
  timeout: 10000,
  headers: {
    "Content-Type": "application/json",
  },
});

// 요청 인터셉터
instance.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("accessToken");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error),
);

// 응답 인터셉터
instance.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // 토큰 만료 처리 (로그아웃, 토큰 갱신 등)
      localStorage.removeItem("accessToken");
      window.location.href = "/login";
    }
    return Promise.reject(error);
  },
);

export default instance;
