import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/** Compose conditional Tailwind classes; later classes win on conflicts. */
export const cn = (...inputs: ClassValue[]) => twMerge(clsx(inputs));
