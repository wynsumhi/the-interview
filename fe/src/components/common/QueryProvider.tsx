"use client";

// App Router의 layout.tsx는 서버 컴포넌트이므로
// QueryClientProvider(클라이언트 전용)를 별도 파일로 분리
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";

export default function QueryProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  // useState로 생성해야 요청마다 새 인스턴스가 만들어지는 것을 방지
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            // 탭 포커스 시 자동 재요청 비활성화 (히스토리 목록은 빈번한 갱신 불필요)
            refetchOnWindowFocus: false,
            // 실패 시 1회만 재시도
            retry: 1,
          },
        },
      }),
  );

  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}
