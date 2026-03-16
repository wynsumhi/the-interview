// src/middleware.ts
// Next.js 미들웨어 - 모든 요청이 페이지에 도달하기 전에 실행되는 서버 코드

import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

// 로그인 없이 접근 불가능한 경로 목록
const protectedRoutes = ["/archive", "/mypage", "/emotion", "/explore", "/interview"];

// 로그인 상태에서 접근 불가능한 경로 (이미 로그인한 사용자는 필요 없는 페이지)
const authOnlyRoutes = ["/login", "/signup"];

export function middleware(request: NextRequest) {
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
  matcher: ["/((?!_next|api|favicon.ico).*)"],
};
