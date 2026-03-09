import Link from "next/link";

const WAVE_HEIGHTS = [28, 46, 70, 108, 58, 130, 76, 94, 52, 118, 70, 88, 40, 112, 58, 76, 34, 94, 52, 70];

export default function IntroSection() {
  return (
    <section className="relative min-h-screen overflow-hidden bg-[#faf9f6]">
      {/* 그라디언트 orb */}
      <div className="absolute inset-0 pointer-events-none overflow-hidden">
        <div
          className="absolute -top-48 -right-32 w-[750px] h-[750px] rounded-full animate-float-orb"
          style={{
            background:
              "radial-gradient(circle at 35% 40%, #e8d5f5 0%, #f5c6d0 38%, #ffd4a3 65%, transparent 85%)",
            filter: "blur(64px)",
            opacity: 0.55,
          }}
        />
        <div
          className="absolute -bottom-32 -left-48 w-[550px] h-[550px] rounded-full"
          style={{
            background:
              "radial-gradient(circle, #c5e8e0 0%, #b8d4f5 55%, transparent 80%)",
            filter: "blur(80px)",
            opacity: 0.28,
            animation: "float-orb 12s ease-in-out infinite 4s",
          }}
        />
      </div>

      {/* 메인 콘텐츠 */}
      <div className="relative z-10 max-w-[1400px] mx-auto px-8 lg:px-16 min-h-screen flex items-center pt-24 pb-16">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-16 w-full items-center">

          {/* 왼쪽: 텍스트 */}
          <div>
            <div className="flex items-center gap-3 mb-8">
              <span className="block w-10 h-px bg-[#8a9e8c]" />
              <span className="font-caveat text-xl text-[#8a9e8c] tracking-wide">
                Vol.01 — 나를 발견하는 인터뷰
              </span>
            </div>

            <h1 className="text-[72px] lg:text-[96px] xl:text-[116px] font-black leading-[0.87] text-[#1c1c1c] tracking-tight">
              방구석
            </h1>
            <div className="flex items-baseline gap-5 mb-8">
              <h1 className="text-[72px] lg:text-[96px] xl:text-[116px] font-black leading-[0.87] text-[#1c1c1c] tracking-tight">
                인터뷰
              </h1>
              <span className="font-playfair italic text-[28px] lg:text-[40px] text-[#c9a99a] leading-none hidden sm:inline">
                Interview.
              </span>
            </div>

            <p className="font-gaegu text-[26px] text-[#9b91d4] mb-10 -rotate-1 inline-block">
              집에서 나를 인터뷰하는 공간 ✦
            </p>

            <p className="text-[#2d2d2d] text-[17px] leading-[1.95] mb-12 max-w-[480px]">
              AI 인터뷰어가 당신에게 진심어린 질문을 건넵니다.
              <br />
              목소리로 답하고, 나만의 매거진으로 아카이빙하세요.
            </p>

            <div className="flex flex-col sm:flex-row items-start sm:items-center gap-5">
              <Link
                href="/signup"
                className="bg-[#1c1c1c] text-[#faf9f6] px-9 py-4 text-sm font-semibold tracking-[0.18em] uppercase hover:bg-[#c9a99a] transition-colors duration-300"
              >
                인터뷰 시작하기
              </Link>
              <Link
                href="#why"
                className="flex items-center gap-2 text-sm text-[#2d2d2d] font-medium tracking-wide group"
              >
                서비스 소개
                <span className="transition-transform duration-200 group-hover:translate-y-0.5 inline-block">
                  ↓
                </span>
              </Link>
            </div>
          </div>

          {/* 오른쪽: 인터뷰 카드 비주얼 */}
          <div className="hidden lg:flex justify-center items-center">
            <div className="relative">
              <div
                className="absolute -top-4 -right-4 w-full h-full"
                style={{ background: "#8a9e8c", opacity: 0.18 }}
              />
              <div
                className="absolute top-5 left-5 w-full h-full"
                style={{ background: "#c9a99a", opacity: 0.2 }}
              />

              <div className="relative bg-white/72 backdrop-blur-lg border border-white/60 p-10 w-[390px] shadow-2xl">
                <div className="flex items-center justify-between mb-10">
                  <span className="font-playfair italic text-[#c9a99a] text-sm tracking-wide">
                    Interview
                  </span>
                  <span className="text-[#8a9e8c] text-[11px] tracking-[0.22em] font-medium">
                    Q.01
                  </span>
                </div>

                <p className="text-[#1c1c1c] text-[17px] font-bold leading-[1.75] mb-10">
                  오늘 하루 중 가장 인상 깊었던
                  <br />
                  순간은 언제였나요?
                </p>

                {/* 웨이브폼 */}
                <div className="flex items-center gap-[3px] h-[72px] mb-6 px-1">
                  {WAVE_HEIGHTS.map((h, i) => (
                    <div
                      key={i}
                      className="flex-1 rounded-full animate-wave-bar"
                      style={{
                        height: `${h}px`,
                        background:
                          i % 3 === 0
                            ? "#9b91d4"
                            : i % 3 === 1
                            ? "#c9a99a"
                            : "#8a9e8c",
                        animationDelay: `${i * 0.075}s`,
                        opacity: 0.55 + (i % 5) * 0.09,
                      }}
                    />
                  ))}
                </div>

                <div className="flex items-center justify-between pt-5 border-t border-[#e5e0d8]">
                  <div className="flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-[#c9a99a] animate-pulse" />
                    <span className="font-gaegu text-[#8a9e8c] text-[18px]">
                      녹음 중
                    </span>
                  </div>
                  <span className="font-playfair italic text-[#c9a99a] text-sm">
                    02:34
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* 스크롤 힌트 */}
      <div className="absolute bottom-10 left-1/2 -translate-x-1/2 flex flex-col items-center gap-3 opacity-35">
        <span className="text-[10px] tracking-[0.3em] text-[#1c1c1c] uppercase">
          Scroll
        </span>
        <div className="w-px h-10 bg-[#1c1c1c] animate-scroll-line" />
      </div>
    </section>
  );
}
