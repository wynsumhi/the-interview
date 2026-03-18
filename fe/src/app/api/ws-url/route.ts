// 서버사이드 API_URL을 클라이언트에 전달 (WebSocket 직접 연결용)
export function GET() {
  const apiUrl = process.env.API_URL ?? "http://localhost:8080";
  return Response.json({ apiUrl });
}
