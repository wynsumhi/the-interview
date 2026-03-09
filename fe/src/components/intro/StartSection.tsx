import Link from "next/link";

export default function StartSection() {
  return (
    <section className="relative overflow-hidden py-40 px-8 lg:px-16">
      {/* 그라디언트 배경 */}
      <div
        className="absolute inset-0 animate-gradient"
        style={{
          background:
            "linear-gradient(-45deg, #e8d5f5, #f5c6d0, #ffd4a3, #c5e8e0)",
        }}
      />

      {/* 텍스처 오버레이 (grain) */}
      <div
        className="absolute inset-0 opacity-[0.15] pointer-events-none"
        style={{
          backgroundImage: `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='300' height='300'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='300' height='300' filter='url(%23n)' opacity='1'/%3E%3C/svg%3E")`,
          backgroundSize: "200px 200px",
        }}
      />

      <div className="relative z-10 max-w-[1400px] mx-auto text-center">
        <p className="font-caveat text-[22px] text-[#1c1c1c]/55 mb-6">
          지금 바로 시작해보세요
        </p>

        <h2 className="text-[48px] lg:text-[68px] xl:text-[84px] font-black text-[#1c1c1c] leading-[1.1] tracking-tight mb-3">
          당신의 이야기가
        </h2>
        <h2 className="font-gaegu text-[48px] lg:text-[68px] xl:text-[84px] text-[#1c1c1c] leading-[1.15] mb-16">
          매거진이 됩니다.
        </h2>

        <Link
          href="/signup"
          className="inline-block bg-[#1c1c1c] text-[#faf9f6] px-14 py-5 text-sm font-semibold tracking-[0.22em] uppercase hover:bg-white hover:text-[#1c1c1c] transition-all duration-300"
        >
          무료로 시작하기
        </Link>

        <p className="mt-8 text-[#1c1c1c]/45 text-[13px]">
          이미 계정이 있으신가요?{" "}
          <Link
            href="/login"
            className="underline underline-offset-2 hover:text-[#1c1c1c]/80 transition-colors"
          >
            로그인
          </Link>
        </p>
      </div>
    </section>
  );
}
