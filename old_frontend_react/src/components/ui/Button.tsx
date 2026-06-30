import { type ButtonHTMLAttributes } from "react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "../../utils/cn";

export const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 rounded-xl font-semibold transition-all " +
    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-600/40 " +
    "focus-visible:ring-offset-2 focus-visible:ring-offset-canvas " +
    "disabled:pointer-events-none disabled:opacity-50 active:scale-[0.98]",
  {
    variants: {
      variant: {
        primary: "bg-brand-700 text-white shadow-sm hover:bg-brand-800 hover:shadow-md",
        secondary: "border border-line bg-surface text-ink-strong shadow-sm hover:bg-zinc-50",
        success: "bg-emerald-700 text-white shadow-sm hover:bg-emerald-800",
        danger: "border border-rose-200 bg-surface text-rose-700 hover:bg-rose-50",
        ghost: "text-ink-muted hover:bg-zinc-100 hover:text-ink-strong",
        outline: "border border-line bg-transparent text-ink-strong hover:bg-zinc-50",
      },
      size: {
        sm: "min-h-[2.25rem] px-3 text-base",
        md: "min-h-[3rem] px-5 py-2.5 text-lg",
        icon: "h-11 w-11",
      },
    },
    defaultVariants: { variant: "primary", size: "md" },
  }
);

interface ButtonProps
  extends ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  block?: boolean;
}

export default function Button({ variant, size, block, className, ...props }: ButtonProps) {
  return (
    <button className={cn(buttonVariants({ variant, size }), block && "w-full", className)} {...props} />
  );
}
