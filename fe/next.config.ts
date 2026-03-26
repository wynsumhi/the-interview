import type { NextConfig } from "next";

const API_URL = process.env.API_URL;
if (!API_URL) {
  throw new Error(
    "[next.config.ts] API_URL 환경 변수가 설정되지 않았습니다. .env.local 파일을 확인하고 개발 서버를 재시작해 주세요.",
  );
}

const nextConfig: NextConfig = {
  async rewrites() {
    return {
      // beforeFiles: 파일시스템(App Router API 라우트 포함) 체크 이전에 적용
      // 기본값인 afterFiles는 App Router가 /api/* 경로를 먼저 가로채므로
      // /api/v1/** 요청이 원격 서버로 전달되지 않는 문제가 발생함
      beforeFiles: [
        {
          source: "/api/v1/:path*",
          destination: `${API_URL}/api/v1/:path*`,
        },
        {
          source: "/socket",
          destination: `${API_URL}/ws-connect`,
        },
      ],
      afterFiles: [],
      fallback: [],
    };
  },
};

export default nextConfig;
