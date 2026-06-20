import { type ReactNode } from "react";
import Logo from "../landing/Logo";

function BrandMark() {
  return <Logo />;
}

export default function AuthLayout({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string;
  subtitle?: string;
  children: ReactNode;
  footer?: ReactNode;
}) {
  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      {/* Decorative hero panel (desktop only) */}
      <div className="relative hidden lg:block">
        <img
          src="/illustrations/auth-hero.webp"
          alt=""
          aria-hidden
          className="absolute inset-0 h-full w-full object-cover"
        />
        <div className="absolute inset-0 bg-gradient-to-r from-white/70 via-white/30 to-transparent" />
        <div className="relative flex h-full flex-col p-12">
          <BrandMark />
          <div className="mt-20 max-w-sm">
            <h2 className="text-4xl font-semibold leading-tight tracking-tight text-ink-strong">
              İlaçlarınız güvende, hatırlatmalar sizinle.
            </h2>
            <p className="mt-4 text-lg text-ink">
              Doz takibi, stok ve son kullanma uyarıları, sesli prospektüs ve yapay zekâ
              asistanı — hepsi tek, sade bir yerde.
            </p>
          </div>
        </div>
      </div>

      {/* Form panel */}
      <div className="flex items-center justify-center bg-canvas p-6">
        <div className="w-full max-w-md">
          <div className="mb-8 lg:hidden">
            <BrandMark />
          </div>
          <div className="card p-8">
            <h1 className="text-3xl font-semibold tracking-tight text-ink-strong">{title}</h1>
            {subtitle && <p className="mt-2 text-lg text-ink-muted">{subtitle}</p>}
            <div className="mt-6">{children}</div>
          </div>
          {footer && <p className="mt-6 text-center text-lg text-ink">{footer}</p>}
        </div>
      </div>
    </div>
  );
}
