import { forwardRef, type InputHTMLAttributes, type ReactNode } from "react";
import { cn } from "../../utils/cn";

/** Labelled form control wrapper. */
export function Field({
  label,
  hint,
  error,
  children,
}: {
  label: ReactNode;
  hint?: ReactNode;
  error?: ReactNode;
  children: ReactNode;
}) {
  return (
    <label className="block space-y-1.5">
      <span className="text-base font-medium text-ink-strong">{label}</span>
      {children}
      {hint && !error && <span className="block text-base text-ink-muted">{hint}</span>}
      {error && (
        <span role="alert" className="block text-base text-rose-700">
          {error}
        </span>
      )}
    </label>
  );
}

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  ({ className, ...props }, ref) => <input ref={ref} className={cn("input", className)} {...props} />
);
Input.displayName = "Input";
