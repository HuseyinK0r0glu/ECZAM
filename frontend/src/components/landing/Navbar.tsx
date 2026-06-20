import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { motion } from "motion/react";
import { buttonVariants } from "../ui";
import { cn } from "../../utils/cn";
import Logo from "./Logo";

const NAV_LINKS = [
  { label: "Özellikler", href: "#ozellikler" },
  { label: "Nasıl çalışır", href: "#nasil-calisir" },
  { label: "Yorumlar", href: "#yorumlar" },
  { label: "Güvenlik", href: "#guvenlik" },
];

export default function Navbar() {
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  return (
    <motion.header
      initial={{ opacity: 0, y: -12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
      className={cn(
        "sticky top-0 z-50 transition-colors duration-300",
        scrolled
          ? "border-b border-line bg-white/80 backdrop-blur-md"
          : "border-b border-transparent bg-transparent"
      )}
    >
      <nav className="mx-auto flex h-16 max-w-7xl items-center justify-between gap-6 px-5 sm:px-8">
        {/* Logo */}
        <Link to="/" className="flex shrink-0 items-center" aria-label="ECZAM ana sayfa">
          <Logo />
        </Link>

        {/* Center links */}
        <div className="hidden items-center gap-1 md:flex">
          {NAV_LINKS.map((link) => (
            <a
              key={link.href}
              href={link.href}
              className="rounded-lg px-3 py-2 text-base font-medium text-ink-muted transition-colors hover:text-ink-strong"
            >
              {link.label}
            </a>
          ))}
        </div>

        {/* Right actions */}
        <div className="flex shrink-0 items-center gap-1 sm:gap-2">
          <Link
            to="/login"
            className={cn(buttonVariants({ variant: "ghost", size: "sm" }), "font-medium")}
          >
            Giriş yap
          </Link>
          <Link to="/register" className={buttonVariants({ variant: "primary", size: "sm" })}>
            Ücretsiz başla
          </Link>
        </div>
      </nav>
    </motion.header>
  );
}
