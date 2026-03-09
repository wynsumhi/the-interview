export default function WhySection() {
  return (
    <section id="why" className="bg-[#f5f0eb] py-32 px-8 lg:px-16">
      <div className="max-w-[1400px] mx-auto">

        {/* 섹션 번호 헤더 */}
        <div className="flex items-end gap-4 mb-20">
          <span className="font-playfair text-[100px] lg:text-[140px] font-black text-[#e5e0d8] leading-none select-none">
            01
          </span>
          <div className="mb-4">
            <p className="font-caveat text-xl text-[#8a9e8c] mb-2">
              방구석 인터뷰란?
            </p>
            <div className="w-48 h-px bg-[#c9a99a]" />
          </div>
        </div>

        {/* 2단 그리드 */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-16 lg:gap-28 items-start">

          {/* 왼쪽 */}
          <div>
            <h2 className="text-[36px] lg:text-[48px] font-black text-[#1c1c1c] leading-[1.15] mb-8 tracking-tight">
              AI가 진행하는
              <br />
              나만의 인터뷰
            </h2>
            <p className="text-[#2d2d2d] text-[17px] leading-[1.95] mb-6">
              방구석 인터뷰는 AI가 당신과 1:1 인터뷰를 진행하는 서비스입니다.
              매 세션마다 새로운 질문으로 당신의 생각, 감정, 경험을 탐색합니다.
            </p>
            <p className="text-[#2d2d2d] text-[17px] leading-[1.95]">
              인터뷰가 끝나면 자동으로 매거진 형태로 아카이빙되어,
              시간이 지나도 그날의 나를 다시 만날 수 있습니다.
            </p>

            {/* 손글씨 포인트 */}
            <p className="font-gaegu text-[22px] text-[#9b91d4] mt-10 -rotate-[1.5deg] inline-block">
              "오늘의 나를 기록한다는 것" ✦
            </p>
          </div>

          {/* 오른쪽: 인용구 */}
          <div className="relative">
            <div className="absolute -left-4 top-0 bottom-0 w-[3px] rounded-full bg-gradient-to-b from-[#c9a99a] to-[#9b91d4]" />
            <div className="pl-8">
              <p className="font-playfair italic text-[22px] lg:text-[30px] text-[#1c1c1c] leading-[1.55] mb-10">
                "말하는 것만으로도
                <br />
                생각이 정리된다."
              </p>

              {/* 키워드 태그 */}
              <div className="flex flex-wrap gap-3 mb-8">
                {["녹음", "질문", "아카이브", "매거진", "성찰"].map((tag) => (
                  <span
                    key={tag}
                    className="border border-[#c9a99a] text-[#c9a99a] text-xs tracking-widest px-4 py-1.5"
                  >
                    {tag}
                  </span>
                ))}
              </div>

              <p className="text-[#5d5d5d] text-[15px] leading-[1.9]">
                혼자이지만 혼자가 아닌 인터뷰 경험을 통해
                <br />
                나 자신과 더 깊은 대화를 나눠보세요.
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
