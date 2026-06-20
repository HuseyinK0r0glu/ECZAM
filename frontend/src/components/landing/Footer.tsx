import { Link } from "react-router-dom";
import { AtSign, MessageCircle, Globe, Send } from "lucide-react";
import Logo from "./Logo";

type FooterColumn = {
  title: string;
  id?: string;
  links: { label: string; href: string }[];
};

const COLUMNS: FooterColumn[] = [
  {
    title: "Ürün",
    links: [
      { label: "Özellikler", href: "#ozellikler" },
      { label: "Doz hatırlatmaları", href: "#ozellikler" },
      { label: "Stok takibi", href: "#ozellikler" },
      { label: "Prospektüs asistanı", href: "#ozellikler" },
    ],
  },
  {
    title: "Kaynaklar",
    links: [
      { label: "Nasıl çalışır", href: "#nasil-calisir" },
      { label: "Sık sorulan sorular", href: "#" },
      { label: "Erişilebilirlik", href: "#" },
      { label: "Yardım merkezi", href: "#" },
    ],
  },
  {
    title: "Şirket",
    links: [
      { label: "Hakkımızda", href: "#" },
      { label: "Blog", href: "#" },
      { label: "Kariyer", href: "#" },
      { label: "Kullanıcı yorumları", href: "#yorumlar" },
    ],
  },
  {
    title: "Yasal",
    id: "guvenlik",
    links: [
      { label: "KVKK aydınlatma metni", href: "#" },
      { label: "Gizlilik politikası", href: "#" },
      { label: "Kullanım koşulları", href: "#" },
      { label: "Çerez politikası", href: "#" },
    ],
  },
];

const SOCIALS = [
  { label: "E-posta bülteni", icon: AtSign, href: "mailto:destek@eczam.app" },
  { label: "Topluluk", icon: MessageCircle, href: "#" },
  { label: "Web", icon: Globe, href: "#" },
  { label: "Telegram", icon: Send, href: "#" },
];

export default function Footer() {
  return (
    <footer className="bg-zinc-950 py-16 text-zinc-400">
      <div className="mx-auto max-w-7xl px-5 sm:px-8">
        {/* Brand + tagline */}
        <div className="max-w-sm">
          <Link to="/" className="inline-flex items-center" aria-label="ECZAM ana sayfa">
            <Logo tone="inverted" />
          </Link>
          <p className="mt-4 text-base leading-relaxed text-zinc-400">
            İlaçlarınızı güvenli, düzenli ve erişilebilir kılan akıllı ilaç yönetimi.
          </p>
        </div>

        {/* Columns */}
        <div className="mt-12 grid grid-cols-2 gap-8 sm:grid-cols-3 lg:grid-cols-5">
          {COLUMNS.map((col) => (
            <nav key={col.title} id={col.id} className="scroll-mt-24">
              <h3 className="text-sm font-semibold uppercase tracking-wide text-zinc-200">
                {col.title}
              </h3>
              <ul className="mt-4 space-y-3">
                {col.links.map((link) => (
                  <li key={link.label}>
                    <a
                      href={link.href}
                      className="text-base text-zinc-400 no-underline transition-colors hover:text-white"
                    >
                      {link.label}
                    </a>
                  </li>
                ))}
              </ul>
            </nav>
          ))}

          {/* Wider contact column */}
          <div className="col-span-2 sm:col-span-3 lg:col-span-1">
            <h3 className="text-sm font-semibold uppercase tracking-wide text-zinc-200">İletişim</h3>
            <p className="mt-4 text-base leading-relaxed text-zinc-400">
              Sorularınız ve geri bildirimleriniz için buradayız. Genellikle bir iş günü içinde
              yanıtlıyoruz.
            </p>
            <a
              href="mailto:destek@eczam.app"
              className="mt-3 inline-block text-base font-medium text-white no-underline transition-colors hover:text-brand-400"
            >
              destek@eczam.app
            </a>
          </div>
        </div>

        {/* Bottom bar */}
        <div className="mt-14 flex flex-col gap-4 border-t border-zinc-800 pt-8 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm text-zinc-500">
            © {new Date().getFullYear()} ECZAM. Tüm hakları saklıdır.
          </p>
          <div className="flex items-center gap-1">
            {SOCIALS.map(({ label, icon: Icon, href }) => (
              <a
                key={label}
                href={href}
                aria-label={label}
                className="flex h-9 w-9 items-center justify-center rounded-lg text-zinc-500 transition-colors hover:text-white"
              >
                <Icon className="h-5 w-5" aria-hidden />
              </a>
            ))}
          </div>
        </div>
      </div>
    </footer>
  );
}
