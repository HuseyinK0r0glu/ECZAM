import { cn } from "../../utils/cn";

type LogoProps = {
  /** Applied to the wrapper — use for layout/spacing. */
  className?: string;
  /** Render the "ECZAM" wordmark next to the mark. Set false for a mark-only lockup. */
  wordmark?: boolean;
  /** Color scheme: "default" for light backgrounds, "inverted" for the dark footer. */
  tone?: "default" | "inverted";
};

/**
 * ECZAM monogram — a geometric "E" built from four rounded capsule bars, an
 * abstracted nod to a pill form (not a literal pill icon). Flat, single color
 * via `currentColor`; recolor by setting the text color. Reads cleanly at 32px
 * and the mark alone works as a favicon (see public/favicon.svg).
 */
export default function Logo({ className, wordmark = true, tone = "default" }: LogoProps) {
  const inverted = tone === "inverted";
  return (
    <span className={cn("inline-flex items-center gap-2.5", className)}>
      <svg
        viewBox="0 0 24 24"
        fill="currentColor"
        aria-hidden
        className={cn("h-8 w-8 shrink-0", inverted ? "text-white" : "text-brand-700")}
      >
        <rect x="3" y="3" width="5" height="18" rx="2.5" />
        <rect x="3" y="3" width="18" height="5" rx="2.5" />
        <rect x="3" y="9.5" width="12" height="5" rx="2.5" />
        <rect x="3" y="16" width="18" height="5" rx="2.5" />
      </svg>
      {wordmark && (
        <span
          className={cn(
            "text-xl font-semibold tracking-tight",
            inverted ? "text-white" : "text-ink-strong"
          )}
        >
          ECZAM
        </span>
      )}
    </span>
  );
}
