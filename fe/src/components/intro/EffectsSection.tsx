const EFFECTS = [
  {
    symbol: "✦",
    title: "말하기 훈련",
    desc: "생각을 말로 표현하는 연습을 통해 의사소통 능력과 자기 표현력이 자연스럽게 향상됩니다.",
    color: "#c9a99a",
  },
  {
    symbol: "◎",
    title: "사유 공간 제공",
    desc: "바쁜 일상에서 잠시 멈춰 나 자신과 대화하는 시간. 조용한 내면의 공간을 만들어 드립니다.",
    color: "#9b91d4",
  },
  {
    symbol: "△",
    title: "나를 알아가기",
    desc: "반복되는 인터뷰를 통해 내 생각의 패턴과 변화를 발견하고, 자기 이해의 깊이를 더합니다.",
    color: "#8a9e8c",
  },
];

export default function EffectsSection() {
  return (
    <section className="relative bg-[#16161e] py-32 px-8 lg:px-16 overflow-hidden">
      {/* 배경 orb */}
      <div
        className="absolute -top-20 right-0 w-[600px] h-[600px] rounded-full pointer-events-none"
        style={{
          background:
            "radial-gradient(circle, #e8d5f5 0%, #f5c6d0 45%, transparent 70%)",
          filter: "blur(100px)",
          opacity: 0.08,
        }}
      />

      <div className="relative z-10 max-w-[1400px] mx-auto">

        {/* 헤더 */}
        <div className="mb-20">
          <p className="font-caveat text-xl text-[#8a9e8c] mb-5">
            방구석 인터뷰의 효능
          </p>
          <h2 className="font-gaegu text-[52px] lg:text-[76px] text-[#faf9f6] leading-tight -rotate-1 inline-block">
            인터뷰가 주는 것들
          </h2>
        </div>

        {/* 효능 카드 */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-10 lg:gap-16">
          {EFFECTS.map((item) => (
            <div
              key={item.title}
              className="group border-t pt-8"
              style={{ borderColor: item.color + "33" }}
            >
              <div
                className="text-[30px] mb-6 inline-block transition-transform duration-300 group-hover:scale-125 group-hover:rotate-12"
                style={{ color: item.color }}
              >
                {item.symbol}
              </div>
              <h3 className="text-[22px] font-black text-[#faf9f6] mb-4">
                {item.title}
              </h3>
              <p className="text-[#6a6a6a] text-[15px] leading-[1.95]">
                {item.desc}
              </p>
            </div>
          ))}
        </div>

        {/* 데코 타이포 */}
        <div className="mt-28 overflow-hidden">
          <p
            className="font-playfair italic whitespace-nowrap text-[#faf9f6] font-black opacity-[0.04] text-[80px] lg:text-[120px]"
            style={{ letterSpacing: "0.06em" }}
          >
            Interview · Archive · Discovery · Reflect ·
          </p>
        </div>
      </div>
    </section>
  );
}
