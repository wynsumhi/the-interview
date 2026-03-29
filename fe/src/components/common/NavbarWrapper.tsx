"use client"; // 경로를 감지하는 로직을 추가하기위해 네비바를 감싸는 별도의 클라이언트 컴포넌트 작성

import { useParams, usePathname } from "next/navigation";
import Navbar from "./Navbar";

export default function NavbarWrapper() {
  const pathname = usePathname();

  // NavBar를 숨길 경로 리스트
  const hideNavbarPaths = ["/interview/conversation", "/interview/setup"];
  const shouldHideNavbar = hideNavbarPaths.includes(pathname);

  if (shouldHideNavbar) return null;

  return <Navbar />;
}
