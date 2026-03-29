import Navbar from "@/components/common/Navbar";
import IntroSection from "@/components/intro/IntroSection";
import WhySection from "@/components/intro/WhySection";
import HowSection from "@/components/intro/HowSection";
import EffectsSection from "@/components/intro/EffectsSection";
import StartSection from "@/components/intro/StartSection";
import styles from "./page.module.css";

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
      <footer className={styles.footer}>
        <div className={styles.footerInner}>
          <span className={styles.footerLogo}>방구석 인터뷰</span>
          <p className={styles.footerCopy}>© 2025 방구석 인터뷰. All rights reserved.</p>
        </div>
      </footer>
    </>
  );
}
