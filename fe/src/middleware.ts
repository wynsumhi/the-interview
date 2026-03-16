// src/middleware.ts
// Next.js 미들웨어 - 모든 요청이 페이지에 도달하기 전에 실행되는 서버 코드
// 여기서 쿠키를 확인해 비로그인 사용자가 보호 페이지에 접근하면 /login으로 보냄

import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

// 로그인 없이 접근 불가능한 경로 목록
const protectedRoutes = ["/archive", "/mypage", "/emotion", "/explore", "/interview"];

export function middleware(request: NextRequest) {
  // 쿠키에서 accessToken 읽기 (로그인 시 document.cookie로 저장한 값)
  const token = request.cookies.get("accessToken")?.value;

  // 현재 요청 경로가 보호 라우트 중 하나로 시작하는지 확인
  const isProtected = protectedRoutes.some((route) =>
    request.nextUrl.pathname.startsWith(route)
  );

  // 보호 라우트인데 토큰이 없으면 → /login으로 리다이렉트
  if (isProtected && !token) {
    return NextResponse.redirect(new URL("/login", request.url));
  }

  // 그 외 요청은 그대로 통과
  return NextResponse.next();
}

// 미들웨어가 실행될 경로 범위 지정
// /_next/: Next.js 내부 파일, /api/: API 라우트는 제외
export const config = {
  matcher: ["/((?!_next|api|favicon.ico).*)"],
};
