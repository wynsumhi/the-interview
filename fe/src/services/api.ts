/**
 * 공통 Axios 인스턴스
 *
 * 모든 설정(baseURL, 타임아웃, 인증 토큰 주입, 401 처리)은
 * lib/axios.ts 에서 단일 관리됩니다.
 *
 * 403 / 404 등 엔드포인트별 상태 처리는 각 서비스 함수에서 담당합니다.
 * (예: services/interview.ts → fetchInterviewDetail, deleteInterview)
 */
export { default } from "@/lib/axios";
