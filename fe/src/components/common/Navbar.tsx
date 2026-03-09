import Link from "next/link";

export default function Navbar() {
  return (
    <nav className="fixed top-0 left-0 right-0 z-50 flex items-center justify-between px-8 lg:px-16 py-5">
      {/* 배경 blur */}
      <div className="absolute inset-0 bg-[#faf9f6]/70 backdrop-blur-md border-b border-[#e5e0d8]/50 -z-10" />

      {/* 로고 */}
      <Link
        href="/"
        className="font-playfair italic text-[18px] text-[#1c1c1c] tracking-wide hover:text-[#c9a99a] transition-colors duration-300"
      >
        방구석 인터뷰
      </Link>

      {/* 네비 링크 (비로그인) */}
      <div className="flex items-center gap-8">
        <Link
          href="/login"
          className="text-[#2d2d2d] text-sm tracking-wide hover:text-[#1c1c1c] transition-colors"
        >
          로그인
        </Link>
        <Link
          href="/signup"
          className="bg-[#1c1c1c] text-[#faf9f6] text-sm tracking-widest px-6 py-2.5 hover:bg-[#c9a99a] transition-colors duration-300"
        >
          회원가입
        </Link>
      </div>
    </nav>
  );
}
