import { MotionConfig } from "motion/react";
import Navbar from "../components/landing/Navbar";
import Hero from "../components/landing/Hero";
import SocialProof from "../components/landing/SocialProof";
import Footer from "../components/landing/Footer";

export default function LandingPage() {
  return (
    // reducedMotion="user" disables transform/layout animations for users who
    // prefer reduced motion, keeping the page fully usable (WCAG 2.1 AA).
    <MotionConfig reducedMotion="user">
      <div className="min-h-screen scroll-smooth bg-canvas">
        <Navbar />
        <main>
          <Hero />
          <SocialProof />
        </main>
        <Footer />
      </div>
    </MotionConfig>
  );
}
