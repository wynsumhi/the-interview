import Navbar from "@/components/common/Navbar";
import IntroSection from "@/components/intro/IntroSection";
import WhySection from "@/components/intro/WhySection";
import HowSection from "@/components/intro/HowSection";
import EffectsSection from "@/components/intro/EffectsSection";
import StartSection from "@/components/intro/StartSection";

export default function IntroPage() {
  return (
    <>
      <Navbar />
      <main>
        <IntroSection />
        <WhySection />
        <HowSection />
        <EffectsSection />
        <StartSection />
      </main>

      <footer className="bg-[#16161e] py-12 px-8 lg:px-16 border-t border-white/5">
        <div className="max-w-[1400px] mx-auto flex flex-col sm:flex-row items-center justify-between gap-4">
          <span className="font-playfair italic text-[#faf9f6]/50 text-[18px]">
            방구석 인터뷰
          </span>
          <p className="text-[#faf9f6]/25 text-[13px] tracking-wide">
            © 2025 방구석 인터뷰. All rights reserved.
          </p>
        </div>
      </footer>
    </>
  );
}
