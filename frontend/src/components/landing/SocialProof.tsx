import { motion, type Variants } from "motion/react";
import { Star } from "lucide-react";
import { Card } from "../ui";
import { cn } from "../../utils/cn";

type Testimonial = {
  quote: string;
  name: string;
  role: string;
  initials: string;
  /** Spans two columns on desktop to break the grid rhythm. */
  wide?: boolean;
  /** The hero quote — gets the larger type and a teal-tinted surface. */
  featured?: boolean;
};

const TESTIMONIALS: Testimonial[] = [
  {
    quote:
      "Annem üç farklı tansiyon ilacı kullanıyor ve hangisini ne zaman aldığını takip etmek bende sürekli bir endişeydi. ECZAM'a geçtikten sonra her dozu telefona gelen bildirimle veriyoruz; stok azaldığında önceden haber verdiği için eczane gidişlerimizi de planlayabiliyoruz. İlk kez ilaç yönetimi 'kontrol bende' hissi veriyor.",
    name: "Selin Aydın",
    role: "Bakım veren · 68 yaşındaki annesi için",
    initials: "SA",
    wide: true,
    featured: true,
  },
  {
    quote:
      "Tiroit ilacımı aç karnına almam gerekiyor ve sabahları sürekli unutuyordum. Net hatırlatmalar ve sesli prospektüs bu sorunu tamamen bitirdi.",
    name: "Mehmet Korkmaz",
    role: "Hipotiroidi hastası",
    initials: "MK",
  },
  {
    quote:
      "Süresi geçen kutuları ezberlemeye çalışmıyorum artık; ECZAM yaklaşanları turuncu, geçenleri kırmızıyla işaretliyor.",
    name: "Hülya Şahin",
    role: "Tip 2 diyabet hastası",
    initials: "HŞ",
  },
  {
    quote:
      "Hastalarıma 'prospektüsü okuyun' demek yerine ECZAM'daki asistanı öneriyorum. Yan etki sorularına uydurmadan, doğrudan prospektüsün ilgili bölümünü kaynak göstererek yanıt veriyor — bu güveni çok önemsiyorum.",
    name: "Dr. Ayşe Demir",
    role: "Eczacı, Ankara",
    initials: "AD",
    wide: true,
  },
];

const container: Variants = {
  hidden: {},
  show: { transition: { staggerChildren: 0.1 } },
};

const item: Variants = {
  hidden: { opacity: 0, y: 18 },
  show: { opacity: 1, y: 0, transition: { duration: 0.5, ease: [0.16, 1, 0.3, 1] } },
};

export default function SocialProof() {
  return (
    <section id="yorumlar" className="scroll-mt-24 bg-white py-24">
      <div className="mx-auto max-w-7xl px-5 sm:px-8">
        <div className="max-w-2xl">
          <p className="text-sm font-semibold uppercase tracking-[0.18em] text-brand-700">
            Gerçek kullanıcılar
          </p>
          <h2 className="mt-3 text-4xl font-semibold tracking-tight text-ink-strong sm:text-5xl">
            Bakım verenlerin ve hastaların güvendiği düzen
          </h2>
          <p className="mt-4 text-lg leading-relaxed text-zinc-600">
            Kronik tedavi gören kişiler ve onlara bakan aileler ECZAM'ı her gün, her doz için
            kullanıyor.
          </p>
        </div>

        <motion.div
          variants={container}
          initial="hidden"
          whileInView="show"
          viewport={{ once: true, amount: 0.2 }}
          className="mt-14 grid grid-cols-1 gap-6 md:grid-cols-3"
        >
          {TESTIMONIALS.map((t) => (
            <motion.div
              key={t.name}
              variants={item}
              className={cn(t.wide && "md:col-span-2", "h-full")}
            >
              <Card
                className={cn(
                  "card-interactive flex h-full flex-col",
                  t.featured && "bg-brand-50/40"
                )}
              >
                <div className="flex gap-0.5" aria-label="5 üzerinden 5 yıldız" role="img">
                  {Array.from({ length: 5 }).map((_, i) => (
                    <Star key={i} className="h-4 w-4 fill-brand-500 text-brand-500" aria-hidden />
                  ))}
                </div>

                <blockquote
                  className={cn(
                    "mt-4 flex-1 text-pretty text-ink-strong",
                    t.featured ? "text-xl leading-relaxed" : "text-lg leading-relaxed"
                  )}
                >
                  “{t.quote}”
                </blockquote>

                <figcaption className="mt-6 flex items-center gap-3 border-t border-line/70 pt-5">
                  <span
                    aria-hidden
                    className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-brand-100 text-base font-semibold text-brand-700 ring-1 ring-brand-200"
                  >
                    {t.initials}
                  </span>
                  <span className="min-w-0">
                    <span className="block font-semibold text-ink-strong">{t.name}</span>
                    <span className="block text-sm text-ink-muted">{t.role}</span>
                  </span>
                </figcaption>
              </Card>
            </motion.div>
          ))}
        </motion.div>
      </div>
    </section>
  );
}
