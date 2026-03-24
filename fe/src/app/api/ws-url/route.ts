// ── WebSocket baseURL 전략 ───────────────────────────────────────────────────
// 빈 문자열("")을 반환하면 SockJS가 상대 경로(/api/ws-connect)로 연결하여
// Next.js 서버(localhost:3000)가 프록시 역할을 담당합니다.
//
// next.config.ts의 rewrites:
//   /api/ws-connect/:path*  →  ${API_URL}/api/ws-connect/:path*
//
// 이 방식을 사용하면 브라우저가 j14a307.p.ssafy.io 에 직접 요청을 보내지 않으므로
// CORS 정책 위반 없이 WebSocket 연결이 가능합니다.
// ✅ 현재 상태: Next.js 프록시 경유 → j14a307.p.ssafy.io (CORS 우회)
// ─────────────────────────────────────────────────────────────────────────────
export function GET() {
  // 빈 문자열 반환: SockJS URL = "" + "/api/ws-connect" = "/api/ws-connect" (상대 경로)
  // → Next.js 리라이트가 프로덕션 서버로 프록시 처리
  const apiUrl = "";
  return Response.json({ apiUrl });
}
