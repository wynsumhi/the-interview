import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/v1/:path*",
        destination: `${process.env.API_URL}/api/v1/:path*`,
      },
      {
        source: "/socket",
        destination: `${process.env.API_URL}/ws-connect`,
      },
    ];
  },
};

export default nextConfig;
