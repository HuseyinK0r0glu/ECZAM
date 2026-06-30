import { type HTMLAttributes } from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "../../utils/cn";

export const badgeVariants = cva(
  "inline-flex items-center gap-1 rounded-full border px-2.5 py-1 text-sm font-medium",
  {
    variants: {
      variant: {
        neutral: "bg-zinc-100 text-zinc-700 border-zinc-200",
        brand: "bg-brand-50 text-brand-800 border-brand-200",
        success: "bg-emerald-50 text-emerald-800 border-emerald-200",
        warning: "bg-amber-50 text-amber-800 border-amber-200",
        orange: "bg-orange-50 text-orange-800 border-orange-200",
        danger: "bg-rose-50 text-rose-800 border-rose-200",
      },
    },
    defaultVariants: { variant: "neutral" },
  }
);

export type BadgeVariant = NonNullable<VariantProps<typeof badgeVariants>["variant"]>;

interface BadgeProps extends HTMLAttributes<HTMLSpanElement>, VariantProps<typeof badgeVariants> {}

export default function Badge({ variant, className, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ variant }), className)} {...props} />;
}
