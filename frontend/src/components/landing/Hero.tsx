import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { AnimatePresence, motion, useReducedMotion, type Variants } from "motion/react";
import {
  ArrowRight,
  BellRing,
  Check,
  PackageSearch,
  CalendarX2,
  Bot,
  Sparkles,
  Bell,
  WifiOff,
  FileText,
  ShieldCheck,
  Eye,
  Database,
} from "lucide-react";
import { Card, buttonVariants } from "../ui";
import { cn } from "../../utils/cn";

const container: Variants = {
  hidden: {},
  show: { transition: { staggerChildren: 0.08, delayChildren: 0.05 } },
};

const item: Variants = {
  hidden: { opacity: 0, y: 14 },
  show: { opacity: 1, y: 0, transition: { duration: 0.55, ease: [0.16, 1, 0.3, 1] } },
};

/** Compatibility / standards strip — real things ECZAM is built on, not fake logos. */
const STANDARDS = [
  { label: "Web Push API", icon: Bell },
  { label: "Çevrimdışı PWA", icon: WifiOff },
  { label: "Prospektüs RAG", icon: FileText },
  { label: "KVKK uyumlu", icon: ShieldCheck },
  { label: "WCAG 2.1 AA", icon: Eye },
  { label: "OpenFDA", icon: Database },
];

const PILLARS = [
  { icon: BellRing, title: "Akıllı hatırlatmalar", desc: "Doz saatlerinde telefona anlık bildirim." },
  { icon: PackageSearch, title: "Stok takibi", desc: "Her dozda otomatik düşer, azalınca uyarır." },
  { icon: CalendarX2, title: "Son kullanma", desc: "Tarih geçmeden önce proaktif uyarı." },
  { icon: Bot, title: "Prospektüs asistanı", desc: "Sadece prospektüsten, kaynaklı yanıtlar." },
];

export default function Hero() {
  // Looping "dose logged" toast — atmosphere, not focus. Disabled for users who
  // prefer reduced motion (the toast then just stays visible).
  const reduce = useReducedMotion();
  const [toastOn, setToastOn] = useState(true);
  useEffect(() => {
    if (reduce) {
      setToastOn(true);
      return;
    }
    const id = setInterval(() => setToastOn((v) => !v), 2500);
    return () => clearInterval(id);
  }, [reduce]);

  return (
    <section className="relative overflow-x-clip">
      {/* Soft teal wash behind the hero, fading into the page. */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-x-0 top-0 -z-10 h-[640px] bg-gradient-to-b from-brand-50/70 via-canvas to-canvas"
      />

      {/* ── Hero ─────────────────────────────────────────────── */}
      <div className="mx-auto max-w-7xl px-5 pb-20 pt-16 sm:px-8 lg:pb-28 lg:pt-24">
        <div className="grid items-center gap-12 lg:grid-cols-5 lg:gap-8">
          {/* Left: copy (60%) */}
          <motion.div
            variants={container}
            initial="hidden"
            animate="show"
            className="lg:col-span-3"
          >
            <motion.p
              variants={item}
              className="text-sm font-semibold uppercase tracking-[0.18em] text-brand-700"
            >
              Akıllı ilaç yönetimi
            </motion.p>

            <motion.h1
              variants={item}
              className="mt-4 max-w-2xl text-5xl font-semibold leading-[1.05] tracking-tight text-ink-strong sm:text-6xl lg:text-7xl"
            >
              Bir dozu bile
              <br />
              kaçırmayın.
            </motion.h1>

            <motion.p variants={item} className="mt-6 max-w-md text-lg leading-relaxed text-zinc-600">
              ECZAM doz saatlerinizi hatırlatır, aldığınız her ilacı stoğunuzdan otomatik düşer ve
              son kullanma tarihi yaklaşan kutuları siz fark etmeden uyarır.
            </motion.p>

            <motion.div variants={item} className="mt-9 flex flex-wrap items-center gap-3">
              <Link
                to="/register"
                className={cn(buttonVariants({ variant: "primary", size: "md" }), "px-6")}
              >
                Ücretsiz başlayın
              </Link>
              <a
                href="#nasil-calisir"
                className={cn(buttonVariants({ variant: "ghost", size: "md" }), "group")}
              >
                Nasıl çalışır
                <ArrowRight className="h-5 w-5 transition-transform group-hover:translate-x-0.5" aria-hidden />
              </a>
            </motion.div>

            <motion.p variants={item} className="mt-5 text-sm text-ink-muted">
              Kredi kartı gerekmez · Türkçe arayüz · 2 dakikada kurulum
            </motion.p>
          </motion.div>

          {/* Right: product mockup (40%) */}
          <motion.div
            id="nasil-calisir"
            initial={{ opacity: 0, y: 24 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.7, delay: 0.25, ease: [0.16, 1, 0.3, 1] }}
            className="relative scroll-mt-24 lg:col-span-2"
          >
            {/* Secondary card: behind and above the primary, for depth */}
            <div
              aria-hidden
              className="absolute -right-4 -top-8 z-0 hidden w-60 rotate-3 scale-[0.96] rounded-2xl border border-line bg-surface p-4 opacity-90 shadow-md sm:block"
            >
              <div className="flex items-center gap-2 text-sm font-semibold text-ink-strong">
                <Sparkles className="h-4 w-4 text-brand-600" aria-hidden />
                Prospektüs Asistanı
              </div>
              <p className="mt-3 rounded-lg bg-zinc-100 px-3 py-2 text-sm text-ink">
                Parol'u aç karnına alabilir miyim?
              </p>
              <p className="mt-2 text-sm leading-snug text-ink-muted">
                Prospektüse göre tok ya da aç karnına alınabilir.
              </p>
              <span className="mt-2 inline-block rounded-full bg-brand-50 px-2 py-0.5 text-xs font-medium text-brand-700">
                Kaynak: Kullanım şekli
              </span>
            </div>

            {/* Primary card: today's doses */}
            <Card className="relative z-10 rounded-2xl border-line p-0 shadow-xl shadow-zinc-200/60">
              <div className="flex items-center justify-between border-b border-line px-5 py-4">
                <div>
                  <p className="text-sm font-medium text-ink-muted">Bugünün dozları</p>
                  <p className="text-lg font-semibold text-ink-strong">20 Haziran, Cuma</p>
                </div>
                <motion.span
                  initial={{ opacity: 0, scale: 0.8 }}
                  animate={{ opacity: 1, scale: 1 }}
                  transition={{ delay: 0.7, type: "spring", stiffness: 320, damping: 18 }}
                  className="rounded-full bg-brand-700 px-3 py-1 text-sm font-semibold text-white"
                >
                  3/5 alındı
                </motion.span>
              </div>

              <ul className="divide-y divide-line">
                <DoseRow name="Parol 500 mg" time="08:00" state="done" />
                <DoseRow name="Coraspin 100 mg" time="13:00" state="due" />
                <DoseRow name="Euthyrox 50 mcg" time="21:00" state="upcoming" />
              </ul>

              <div className="space-y-2 border-t border-line bg-zinc-50/60 px-5 py-4">
                <div className="flex items-center gap-2.5 rounded-xl bg-amber-50 px-3 py-2 text-sm text-amber-900">
                  <CalendarX2 className="h-4 w-4 shrink-0 text-amber-600" aria-hidden />
                  <span>
                    <strong className="font-semibold">Concor 5 mg</strong> · son kullanmaya 9 gün
                  </span>
                </div>
                <div className="flex items-center gap-2.5 rounded-xl bg-zinc-100 px-3 py-2 text-sm text-ink">
                  <PackageSearch className="h-4 w-4 shrink-0 text-ink-muted" aria-hidden />
                  <span>
                    Stok: <strong className="font-semibold text-ink-strong">Parol</strong> 12 doz kaldı
                  </span>
                </div>
              </div>
            </Card>

            {/* Looping "dose logged" toast in front, bottom-left */}
            <AnimatePresence>
              {toastOn && (
                <motion.div
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: 10 }}
                  transition={{ duration: 0.4, ease: [0.16, 1, 0.3, 1] }}
                  className="absolute -bottom-5 -left-3 z-20 hidden items-center gap-2 rounded-full border border-line bg-surface px-3.5 py-2 shadow-lg shadow-zinc-200/70 sm:flex"
                >
                  <span className="flex h-6 w-6 items-center justify-center rounded-full bg-emerald-600 text-white">
                    <Check className="h-3.5 w-3.5" aria-hidden />
                  </span>
                  <span className="text-sm font-medium text-ink-strong">Doz kaydedildi</span>
                </motion.div>
              )}
            </AnimatePresence>
          </motion.div>
        </div>
      </div>

      {/* ── Trust / compliance strip (own band, subtle bg lift) ── */}
      <div className="bg-white">
        <div className="mx-auto max-w-7xl px-5 py-16 sm:px-8">
          <p className="text-center text-sm font-semibold uppercase tracking-[0.18em] text-brand-700">
            Güvenlik ve erişilebilirlik standartlarına uygun
          </p>
          <div className="group relative mt-8 overflow-hidden [mask-image:linear-gradient(to_right,transparent,black_12%,black_88%,transparent)]">
            {/* Trailing margin per item (not flex `gap`) so the duplicated half
                lands exactly on the original at -50% — a truly seamless loop. */}
            <div className="flex w-max animate-marquee items-center group-hover:[animation-play-state:paused]">
              {[...STANDARDS, ...STANDARDS].map(({ label, icon: Icon }, i) => (
                <span
                  key={i}
                  aria-hidden={i >= STANDARDS.length}
                  className="mr-10 flex items-center gap-2.5 whitespace-nowrap text-zinc-500 transition-colors duration-200 hover:text-zinc-800"
                >
                  <Icon className="h-5 w-5 shrink-0" aria-hidden />
                  <span className="text-lg font-semibold tracking-tight">{label}</span>
                </span>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* ── Feature pillars ──────────────────────────────────── */}
      <div id="ozellikler" className="mx-auto max-w-7xl scroll-mt-24 px-5 py-20 sm:px-8 lg:py-28">
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
          {PILLARS.map(({ icon: Icon, title, desc }) => (
            <div
              key={title}
              className="group rounded-xl bg-white p-6 ring-1 ring-zinc-200/50 transition-all duration-300 ease-out hover:-translate-y-0.5 hover:shadow-md hover:ring-zinc-300"
            >
              <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-brand-50 text-brand-600">
                <Icon className="h-5 w-5" aria-hidden />
              </span>
              <p className="mt-4 text-lg font-semibold tracking-tight text-ink-strong">{title}</p>
              <p className="mt-1.5 text-base leading-relaxed text-ink-muted">{desc}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

function DoseRow({
  name,
  time,
  state,
}: {
  name: string;
  time: string;
  state: "done" | "due" | "upcoming";
}) {
  return (
    <li className="flex items-center justify-between gap-3 px-5 py-3.5">
      <div className="min-w-0">
        <p
          className={cn(
            "truncate font-medium",
            state === "done" ? "text-ink-muted line-through" : "text-ink-strong"
          )}
        >
          {name}
        </p>
        <p className="text-sm text-ink-muted">{time}</p>
      </div>
      {state === "done" && (
        <span className="flex shrink-0 items-center gap-1.5 rounded-full bg-emerald-50 px-3 py-1 text-sm font-medium text-emerald-700">
          <Check className="h-4 w-4" aria-hidden />
          Alındı
        </span>
      )}
      {state === "due" && (
        <button
          type="button"
          className="shrink-0 rounded-full bg-brand-700 px-4 py-1.5 text-sm font-semibold text-white transition-transform duration-150 active:scale-[0.98]"
        >
          Aldım
        </button>
      )}
      {state === "upcoming" && (
        <span className="shrink-0 rounded-full border border-line px-3 py-1 text-sm text-ink-muted">
          Yaklaşan
        </span>
      )}
    </li>
  );
}
