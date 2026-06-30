import { type Variants } from "motion/react";

/**
 * Shared staggered fade-up entry animation — matches the landing page pattern so
 * internal pages feel part of the same system. Use `fadeUpContainer` on a wrapper
 * (initial="hidden" animate="show") and `fadeUpItem` on each child block.
 * Reduced-motion is honored globally via <MotionConfig reducedMotion="user"> in AppShell.
 */
export const fadeUpContainer: Variants = {
  hidden: {},
  show: { transition: { staggerChildren: 0.09, delayChildren: 0.05 } },
};

export const fadeUpItem: Variants = {
  hidden: { opacity: 0, y: 16 },
  show: { opacity: 1, y: 0, transition: { duration: 0.5, ease: [0.16, 1, 0.3, 1] } },
};
