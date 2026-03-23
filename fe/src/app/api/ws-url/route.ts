// ── WebSocket 연결 전환 방법 ────────────────────────────────────────────────
// 로컬 테스트:    아래 "http://localhost:8080" 줄의 주석을 해제하고
//                process.env.API_URL 줄을 주석 처리
// 프로덕션 배포:  process.env.API_URL 줄을 활성화하고 localhost 줄을 주석 처리
//                (.env.local 또는 배포 환경변수의 API_URL 값을 사용)
// ─────────────────────────────────────────────────────────────────────────────
export function GET() {
  const apiUrl =
    // "http://localhost:8080";          // 로컬 테스트용
    process.env.API_URL ?? "http://localhost:8080"; // 프로덕션 (환경변수 경유)
  return Response.json({ apiUrl });
}
