// src/proxy.ts
// Next.js 16+에서 도입된 proxy 파일 컨벤션
// 이전의 middleware.ts를 대체하며, 요청이 페이지에 도달하기 전에 서버에서 실행됨
//
// [middleware.ts → proxy.ts 마이그레이션 이유]
// - Next.js 16부터 "middleware" 파일 컨벤션이 deprecated됨
// - "proxy"라는 이름이 네트워크 경계에서 실행된다는 개념을 더 명확하게 표현
// - next.config.ts의 rewrites()와 middleware.ts가 공존할 때 충돌 감지로 경고 발생
// - 참고: https://nextjs.org/docs/messages/middleware-to-proxy

import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

// 로그인 없이 접근 불가능한 경로 목록
const protectedRoutes = ["/archive", "/mypage", "/emotion", "/explore", "/interview"];

// 로그인 상태에서 접근 불가능한 경로 (이미 로그인한 사용자는 필요 없는 페이지)
const authOnlyRoutes = ["/login", "/signup"];

// middleware() → proxy()로 함수 이름 변경 (Next.js 16 컨벤션)
export function proxy(request: NextRequest) {
  const token = request.cookies.get("accessToken")?.value;
  const { pathname } = request.nextUrl;

  // 비로그인 사용자가 보호 라우트 접근 시 → /login으로 리다이렉트
  const isProtected = protectedRoutes.some((route) => pathname.startsWith(route));
  if (isProtected && !token) {
    return NextResponse.redirect(new URL("/login", request.url));
  }

  // 로그인 사용자가 /login, /signup 접근 시 → /archive로 리다이렉트
  const isAuthPage = authOnlyRoutes.some((route) => pathname.startsWith(route));
  if (isAuthPage && token) {
    return NextResponse.redirect(new URL("/archive", request.url));
  }

  // 로그인 사용자가 / 접근 시 → /archive로 리다이렉트
  if (pathname === "/" && token) {
    return NextResponse.redirect(new URL("/archive", request.url));
  }

  return NextResponse.next();
}

export const config = {
  // next.config.ts의 rewrites()가 처리하는 경로(/api/v1/*, /socket)와 겹치지 않도록 제외
  // _next: Next.js 내부 파일, api: API 라우트, socket: WebSocket 프록시, favicon.ico: 정적 파일
  matcher: ["/((?!_next|api|socket|favicon.ico).*)"],
};
