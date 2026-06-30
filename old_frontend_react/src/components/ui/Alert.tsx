import { type HTMLAttributes, type ReactNode } from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { AlertCircle, AlertTriangle, CheckCircle2, Info } from "lucide-react";
import { cn } from "../../utils/cn";

const alertVariants = cva("flex items-start gap-3 rounded-xl border px-4 py-3 text-base", {
  variants: {
    variant: {
      error: "border-rose-200 bg-rose-50 text-rose-800",
      warning: "border-amber-200 bg-amber-50 text-amber-800",
      success: "border-emerald-200 bg-emerald-50 text-emerald-800",
      info: "border-brand-200 bg-brand-50 text-brand-800",
    },
  },
  defaultVariants: { variant: "info" },
});

const ICONS = { error: AlertCircle, warning: AlertTriangle, success: CheckCircle2, info: Info };

interface AlertProps extends HTMLAttributes<HTMLDivElement>, VariantProps<typeof alertVariants> {
  children: ReactNode;
}

export default function Alert({ variant = "info", className, children, role, ...props }: AlertProps) {
  const Icon = ICONS[variant ?? "info"];
  // Errors/warnings are assertive; success/info are polite.
  const resolvedRole = role ?? (variant === "error" || variant === "warning" ? "alert" : "status");
  return (
    <div role={resolvedRole} className={cn(alertVariants({ variant }), className)} {...props}>
      <Icon className="mt-0.5 h-5 w-5 shrink-0" aria-hidden />
      <div className="min-w-0">{children}</div>
    </div>
  );
}
