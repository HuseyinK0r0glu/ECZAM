import { type ReactNode } from "react";

/**
 * Empty-state placeholder. Pass `image` for a generated illustration, or `icon`
 * for a lucide icon rendered inside a tinted circle.
 */
export default function EmptyState({
  image,
  icon,
  title,
  description,
  action,
}: {
  image?: string;
  icon?: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center justify-center rounded-2xl bg-surface px-6 py-10 text-center shadow-sm ring-1 ring-zinc-200/50">
      {icon ? (
        <div
          className="mb-5 flex h-16 w-16 items-center justify-center rounded-full bg-brand-100 text-brand-700 ring-8 ring-brand-50"
          aria-hidden
        >
          {icon}
        </div>
      ) : image ? (
        <img src={image} alt="" aria-hidden className="mb-5 h-32 w-32 object-contain" />
      ) : null}
      <p className="text-xl font-semibold tracking-tight text-ink-strong">{title}</p>
      {description && <p className="mt-1.5 max-w-sm text-base text-ink-muted">{description}</p>}
      {action && <div className="mt-6">{action}</div>}
    </div>
  );
}
