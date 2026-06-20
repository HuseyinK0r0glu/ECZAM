/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      strategies: "injectManifest",          // custom SW (keeps Phase 4 push handlers)
      srcDir: "src",
      filename: "sw.ts",
      registerType: "autoUpdate",
      manifest: {
        name: "ECZAM — İlaç Takip",
        short_name: "ECZAM",
        description: "Akıllı ilaç yönetimi",
        lang: "tr",
        theme_color: "#0f766e",
        background_color: "#f8fafc",
        display: "standalone",
        start_url: "/",
        icons: [
          { src: "/icon-192.png", sizes: "192x192", type: "image/png" },
          { src: "/icon-512.png", sizes: "512x512", type: "image/png" },
          { src: "/icon-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
        ],
      },
      // SW disabled in dev: it uses CacheFirst for scripts, which serves a stale
      // bundle and hides code changes during `npm run dev`. The SW still ships in
      // production builds — test it with `npm run build && npm run preview`.
      devOptions: { enabled: false, type: "module" },
    }),
  ],
  server: { port: 5173, open: "/" },
  test: { environment: "jsdom", globals: true, setupFiles: "./src/test/setup.ts" },
});
