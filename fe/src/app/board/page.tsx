// 사용자가 주소창에 그냥 /board만 치고 들어왔을 때 게이트웨이 역할 - 접속 시 본인 UUID로 리다이렉트
"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useUserStore } from "@/store/useUserStore";

export default function BoardRootPage() {
  const { userUuid, isLoggedIn } = useUserStore();
  const router = useRouter();

  useEffect(() => {
    // 로그인 상태이고 uuid가 있다면 본인의 페이지로 이동
    if (isLoggedIn && userUuid) {
      router.replace(`/board/${userUuid}`);
    }
    // 로그인 정보가 없다면 로그인 페이지로
    else {
      router.replace("/login");
    }
  }, [isLoggedIn, userUuid, router]);

  return null; // 화면이 바뀌는 동안 아무것도 렌더링하지 않음
}
