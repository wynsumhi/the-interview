const STEPS = [
  {
    num: "01",
    title: "시간 설정",
    desc: "5분 단위로 최대 30분까지\n인터뷰 시간을 설정합니다.",
    color: "#c9a99a",
  },
  {
    num: "02",
    title: "AI 질문",
    desc: "AI 인터뷰어가 음성으로\n질문을 읽어드립니다.",
    color: "#9b91d4",
  },
  {
    num: "03",
    title: "녹음 & 답변",
    desc: "질문이 끝나면 녹음이 시작됩니다.\n편하게 이야기하고 버튼으로 종료.",
    color: "#8a9e8c",
  },
  {
    num: "04",
    title: "매거진 아카이빙",
    desc: "인터뷰가 끝나면 자동으로\n나만의 매거진으로 저장됩니다.",
    color: "#c9a99a",
  },
];

export default function HowSection() {
  return (
    <section id="how" className="bg-[#faf9f6] py-32 px-8 lg:px-16">
      <div className="max-w-[1400px] mx-auto">

        {/* 섹션 번호 헤더 */}
        <div className="flex items-end gap-4 mb-20">
          <span className="font-playfair text-[100px] lg:text-[140px] font-black text-[#e5e0d8] leading-none select-none">
            02
          </span>
          <div className="mb-4">
            <p className="font-caveat text-xl text-[#8a9e8c] mb-2">
              어떻게 진행되나요?
            </p>
            <div className="w-48 h-px bg-[#9b91d4]" />
          </div>
        </div>

        {/* 스텝 그리드 */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-10">
          {STEPS.map((step) => (
            <div key={step.num} className="group">
              {/* 컬러 라인 */}
              <div
                className="h-px mb-8 transition-all duration-500 group-hover:w-full"
                style={{
                  background: step.color,
                  width: "40px",
                }}
              />

              {/* 번호 */}
              <p className="font-playfair italic text-[52px] font-black text-[#e5e0d8] mb-4 leading-none">
                {step.num}
              </p>

              {/* 제목 */}
              <h3 className="text-[19px] font-black text-[#1c1c1c] mb-3">
                {step.title}
              </h3>

              {/* 설명 */}
              <p className="text-[#5d5d5d] text-[14px] leading-[1.95] whitespace-pre-line">
                {step.desc}
              </p>
            </div>
          ))}
        </div>

        {/* 하단 구분선 메시지 */}
        <div className="mt-20 flex items-center gap-6">
          <div className="flex-1 h-px bg-[#e5e0d8]" />
          <p className="font-gaegu text-[20px] text-[#9b91d4] shrink-0">
            질문은 매 인터뷰마다 새롭게 생성됩니다
          </p>
          <div className="flex-1 h-px bg-[#e5e0d8]" />
        </div>
      </div>
    </section>
  );
}
