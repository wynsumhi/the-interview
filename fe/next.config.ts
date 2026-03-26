import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      // REST API: /api/v1/* → 프로덕션 서버 /api/v1/*
      {
        source: "/api/v1/:path*",
        destination: `${process.env.API_URL}/api/v1/:path*`,
      },
      // WebSocket(SockJS): /api/ws-connect/* → 프로덕션 서버 /api/ws-connect/*
      // 브라우저가 직접 j14a307.p.ssafy.io 에 연결하지 않도록 Next.js 서버 경유 프록시
      // (직접 연결 시 CORS 차단됨 — Origin: localhost:3000 불허)
      {
        source: "/api/ws-connect/:path*",
        destination: `${process.env.API_URL}/api/ws-connect/:path*`,
      },
    ];
  },
};

export default nextConfig;
