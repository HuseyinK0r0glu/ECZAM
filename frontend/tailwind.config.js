/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        // Calm Teal brand primary (approved design system)
        brand: {
          50: "#f0fdfa",
          100: "#ccfbf1",
          200: "#99f6e4",
          300: "#5eead4",
          400: "#2dd4bf",
          500: "#14b8a6",
          600: "#0d9488",
          700: "#0f766e",
          800: "#115e59",
          900: "#134e4a",
        },
        canvas: "#fafafa", // app background (zinc-50)
        surface: "#ffffff", // cards
        line: "#e4e4e7", // hairline borders (zinc-200)
        ink: {
          DEFAULT: "#3f3f46", // body text (zinc-700)
          strong: "#18181b", // headings (zinc-900)
          muted: "#71717a", // secondary text (zinc-500)
        },
      },
      fontFamily: {
        sans: ['"Inter Variable"', "Inter", "system-ui", "sans-serif"],
      },
      fontSize: { base: "1.125rem" }, // larger default for accessibility (P1)
      borderRadius: { xl: "0.875rem", "2xl": "1.25rem" },
      boxShadow: {
        // Layered, premium-feeling elevations
        card: "0 1px 2px -1px rgb(15 23 42 / 0.05), 0 1px 3px 0 rgb(15 23 42 / 0.06)",
        "card-hover": "0 10px 24px -8px rgb(15 23 42 / 0.16), 0 2px 6px -2px rgb(15 23 42 / 0.08)",
        nav: "0 -1px 4px 0 rgb(15 23 42 / 0.06)",
        sidebar: "1px 0 0 0 rgb(226 232 240 / 1)",
        ring: "0 0 0 1px rgb(15 23 42 / 0.04)",
      },
      keyframes: {
        "fade-in": {
          from: { opacity: "0", transform: "translateY(4px)" },
          to: { opacity: "1", transform: "translateY(0)" },
        },
        // Continuous logo strip; track holds two copies so -50% loops seamlessly.
        marquee: {
          from: { transform: "translateX(0)" },
          to: { transform: "translateX(-50%)" },
        },
      },
      animation: {
        "fade-in": "fade-in 0.2s ease-out both",
        marquee: "marquee 32s linear infinite",
      },
    },
  },
  plugins: [],
};
