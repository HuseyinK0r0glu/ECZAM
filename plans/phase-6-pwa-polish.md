# Phase 6 — PWA & Polish

> **Goal:** make ECZAM an installable, offline-capable PWA; finalize the dashboard;
> meet WCAG 2.1 AA and the 375px viewport target; and harden the backend (health,
> security headers, rate limiting).
>
> **Realizes:** EP-09 · FR-100…103 · NFR-010…015, NFR-070…072 · SEC-* hardening.
> **Prerequisites:** [phase-5-ai-assistant-tts.md](phase-5-ai-assistant-tts.md).
> **Exit criteria:** Lighthouse PWA + accessibility checks pass; dashboard summarizes
> today's doses, low stock, and expiry; rate limiting + security headers in place.

---

## 1. Dependencies

Frontend:

```bash
cd frontend && npm i -D vite-plugin-pwa && npm i workbox-window
# workbox libs used inside the custom SW:
npm i -D workbox-precaching workbox-routing workbox-strategies
```

Backend — add to `pom.xml` for rate limiting (Actuator is already present from Phase 1):

```xml
<dependency><groupId>com.bucket4j</groupId><artifactId>bucket4j_jdk17-core</artifactId><version>8.10.1</version></dependency>
```

---

## 2. Frontend — PWA

### `frontend/vite.config.ts` (replace Phase 1 version)

```ts
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
        theme_color: "#1d4ed8",
        background_color: "#ffffff",
        display: "standalone",
        start_url: "/",
        icons: [
          { src: "/icon-192.png", sizes: "192x192", type: "image/png" },
          { src: "/icon-512.png", sizes: "512x512", type: "image/png" },
          { src: "/icon-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
        ],
      },
      devOptions: { enabled: true, type: "module" },
    }),
  ],
  server: { port: 5173 },
  test: { environment: "jsdom", globals: true, setupFiles: "./src/test/setup.ts" },
});
```

### `frontend/src/sw.ts` (replaces `public/sw.js` from Phase 4)

```ts
/// <reference lib="webworker" />
import { precacheAndRoute } from "workbox-precaching";
import { registerRoute } from "workbox-routing";
import { CacheFirst, NetworkFirst } from "workbox-strategies";

declare const self: ServiceWorkerGlobalScope & { __WB_MANIFEST: Array<{ url: string; revision: string | null }> };

// Precache the built static assets (injected at build time).
precacheAndRoute(self.__WB_MANIFEST);

// Cache-first for static assets (NFR-072).
registerRoute(
  ({ request }) => ["style", "script", "image", "font"].includes(request.destination),
  new CacheFirst({ cacheName: "static-assets" }),
);

// Network-first for API calls, with offline fallback to cache.
registerRoute(
  ({ url }) => url.pathname.startsWith("/api/v1"),
  new NetworkFirst({ cacheName: "api", networkTimeoutSeconds: 5 }),
);

// Offline fallback page for navigations.
registerRoute(
  ({ request }) => request.mode === "navigate",
  new NetworkFirst({ cacheName: "pages" }),
);

// --- Push handling (from Phase 4) ---
self.addEventListener("push", (event: PushEvent) => {
  const payload = event.data ? event.data.json() : {};
  const { title = "ECZAM", body = "", data = {} } = payload;
  const actions = data.action === "MARK_TAKEN" ? [{ action: "mark-taken", title: "✓ Aldım" }] : [];
  event.waitUntil(self.registration.showNotification(title, { body, data, actions, icon: "/icon-192.png" }));
});

self.addEventListener("notificationclick", (event: NotificationEvent) => {
  event.notification.close();
  const data = event.notification.data || {};
  const url = event.action === "mark-taken" && data.userMedicationId
    ? `/?logDose=${data.userMedicationId}&scheduleId=${data.scheduleId || ""}`
    : "/";
  event.waitUntil(self.clients.openWindow(url));
});
```

> Remove `public/sw.js`; registration now happens via `vite-plugin-pwa`. Update the push
> subscription flow (Phase 4 `pushService.ts`) to use the Workbox-registered SW: replace
> `navigator.serviceWorker.register("/sw.js")` with `import { registerSW } from
> "virtual:pwa-register"` at app startup and read the ready registration from
> `navigator.serviceWorker.ready` before `pushManager.subscribe`.

### `frontend/src/pages/Offline.tsx`

```tsx
export default function Offline() {
  return (
    <main className="mx-auto max-w-md p-8 text-center">
      <h1 className="text-3xl font-bold">Çevrimdışısınız</h1>
      <p className="mt-4 text-lg text-gray-700">
        İnternet bağlantısı yok. Daha önce görüntülenen bilgiler kullanılabilir;
        yeni veriler bağlantı geri geldiğinde güncellenecek.
      </p>
    </main>
  );
}
```

### `frontend/index.html` — link manifest meta

```html
<!-- add inside <head> -->
<meta name="theme-color" content="#1d4ed8" />
<link rel="apple-touch-icon" href="/icon-192.png" />
```

---

## 3. Frontend — Dashboard (EP-09)

### `frontend/src/utils/schedule.ts`

```ts
import type { ScheduleView } from "../services/scheduleService";

/** Mirror of the backend isDue rule, for "due today" filtering on the dashboard. */
export function isDueToday(s: ScheduleView, today = new Date()): boolean {
  if (!s.active) return false;
  const date = today.toISOString().slice(0, 10);
  if (date < s.startsOn) return false;
  if (s.endsOn && date > s.endsOn) return false;
  const isoDow = today.getDay() === 0 ? 7 : today.getDay(); // 1=Mon..7=Sun
  if (s.frequencyType === "weekly") return (s.daysOfWeek ?? []).includes(isoDow);
  if (s.frequencyType === "interval") {
    const start = new Date(s.startsOn);
    const days = Math.round((today.getTime() - start.getTime()) / 86_400_000);
    return (s.frequencyValue ?? 1) > 0 && days % (s.frequencyValue ?? 1) === 0;
  }
  return true; // daily
}
```

### `frontend/src/pages/Dashboard.tsx` (replace Phase 1 placeholder)

```tsx
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";
import { listInventory } from "../services/inventoryService";
import { listSchedules } from "../services/scheduleService";
import { apiClient } from "../services/apiClient";
import type { ApiResponse } from "../types";
import type { InventoryItem } from "../services/inventoryService";
import { isDueToday } from "../utils/schedule";
import LogDoseButton from "../features/reminders/LogDoseButton";
import EnablePushPrompt from "../features/notifications/EnablePushPrompt";

export default function Dashboard() {
  const { user, logout } = useAuth();
  const schedules = useQuery({ queryKey: ["schedules"], queryFn: listSchedules });
  const inventory = useQuery({ queryKey: ["inventory"], queryFn: listInventory });
  const expiring = useQuery({
    queryKey: ["expiring-soon"],
    queryFn: async () => (await apiClient.get<ApiResponse<InventoryItem[]>>("/expiration/expiring-soon")).data.data!,
  });

  const today = (schedules.data ?? []).filter((s) => isDueToday(s));
  const lowStock = (inventory.data ?? []).filter((i) => i.lowStock);

  return (
    <main className="mx-auto max-w-2xl p-6">
      <header className="mb-6 flex items-center justify-between">
        <h1 className="text-3xl font-bold">Merhaba{user?.displayName ? `, ${user.displayName}` : ""}</h1>
        <button onClick={logout} className="rounded border px-4 py-2 text-lg">Çıkış</button>
      </header>

      <EnablePushPrompt />

      <section className="mt-6" aria-labelledby="today-h">
        <h2 id="today-h" className="mb-2 text-2xl font-semibold">Bugünün Dozları</h2>
        <ul className="space-y-2">
          {today.map((s) => (
            <li key={s.id} className="flex items-center justify-between rounded border p-3">
              <span className="text-lg">{s.medicationName} · {s.scheduledTimes.join(", ")}</span>
              <LogDoseButton userMedicationId={s.userMedicationId} amount={s.dosageAmount} scheduleId={s.id} />
            </li>
          ))}
          {today.length === 0 && <p className="text-lg text-gray-600">Bugün için planlanmış doz yok.</p>}
        </ul>
      </section>

      <section className="mt-8" aria-labelledby="low-h">
        <h2 id="low-h" className="mb-2 text-2xl font-semibold text-orange-700">Azalan Stok</h2>
        <ul className="space-y-2">
          {lowStock.map((i) => (
            <li key={i.id} className="rounded border border-orange-300 bg-orange-50 p-3 text-lg">
              {i.medicationName} — {i.quantity} {i.unit}
            </li>
          ))}
          {lowStock.length === 0 && <p className="text-lg text-gray-600">Stok yeterli.</p>}
        </ul>
      </section>

      <section className="mt-8" aria-labelledby="exp-h">
        <h2 id="exp-h" className="mb-2 text-2xl font-semibold text-amber-700">Yaklaşan Son Kullanma</h2>
        <ul className="space-y-2">
          {(expiring.data ?? []).map((i) => (
            <li key={i.id} className="rounded border border-amber-300 bg-amber-50 p-3 text-lg">
              {i.medicationName} — {i.expirationDate}
            </li>
          ))}
          {(expiring.data ?? []).length === 0 && <p className="text-lg text-gray-600">Yakında dolacak ilaç yok.</p>}
        </ul>
      </section>

      <nav className="mt-8 flex flex-wrap gap-3 text-lg">
        <Link className="text-blue-700 underline" to="/inventory">Envanter</Link>
        <Link className="text-blue-700 underline" to="/schedules">Programlar</Link>
        <Link className="text-blue-700 underline" to="/logs">Geçmiş</Link>
        <Link className="text-blue-700 underline" to="/expiration">Son Kullanma</Link>
        <Link className="text-blue-700 underline" to="/assistant">Asistan</Link>
      </nav>
    </main>
  );
}
```

---

## 4. Accessibility (NFR-010…015)

A pass over the whole app, not new screens. Checklist:

- **Landmarks & headings:** each page uses `<main>` with a single `<h1>`; sections use
  `aria-labelledby`. (Applied above and in earlier phases.)
- **Color contrast ≥ 4.5:1:** verify Tailwind colors (blue-700 on white, amber/orange-800
  on tint) with a contrast checker; darken any failing pair.
- **Font scaling:** never set viewport `maximum-scale`/`user-scalable=no` (Phase 1
  `index.html` already omits them); use `rem`/Tailwind classes, not fixed px.
- **Keyboard:** every control reachable via Tab with the visible focus ring from
  `index.css`; TTS bar, scanner modal, and chat input operable without a mouse.
- **Forms:** every input wrapped in a `<label>`; errors use `role="alert"`.
- **Live regions:** dose-log confirmation uses `role="status"`; streaming chat updates an
  existing node (announce politely).
- **Targets:** buttons use generous padding (`p-3`/`px-4 py-2`) for low-dexterity users (P1).

Run an **axe** scan (`@axe-core/playwright`) and fix violations; manual keyboard + 200%
zoom walkthrough.

---

## 5. Backend hardening

### Security headers + HSTS — extend `SecurityConfig` (Phase 1)

```java
// inside filterChain(...), add to the HttpSecurity chain:
.headers(h -> h
    .frameOptions(fo -> fo.deny())
    .contentTypeOptions(co -> {})
    .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
    .referrerPolicy(rp -> rp.policy(
        org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
    .addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter(
        "Content-Security-Policy",
        "default-src 'self'; img-src 'self' data:; connect-src 'self'; "
      + "script-src 'self'; style-src 'self' 'unsafe-inline'")))
```

> CSP is served from the API; the static PWA host should send its own CSP allowing the
> service worker, camera (`@zxing`/`html5-qrcode`), and Web Speech. Tune `connect-src` to
> include the API origin in production.

### Rate limiting (bucket4j) on `/auth/**` and `/ai/**`

#### `backend/src/main/java/com/eczam/shared/web/RateLimitFilter.java`

```java
package com.eczam.shared.web;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getServletPath();
        boolean limited = path.startsWith("/auth") || path.startsWith("/ai");
        if (limited) {
            String key = clientIp(req) + ":" + (path.startsWith("/auth") ? "auth" : "ai");
            Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(path));
            if (!bucket.tryConsume(1)) {
                res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                res.getWriter().write("{\"data\":null,\"meta\":null,\"error\":{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests\"}}");
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private Bucket newBucket(String path) {
        // auth: 10/min (brute-force guard); ai: 20/min (cost/abuse guard)
        int perMin = path.startsWith("/auth") ? 10 : 20;
        return Bucket.builder().addLimit(Bandwidth.simple(perMin, Duration.ofMinutes(1))).build();
    }

    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return xff != null ? xff.split(",")[0].trim() : req.getRemoteAddr();
    }
}
```

> Spring Security's context-path is `/api/v1`, so `getServletPath()` returns `/auth/...`
> and `/ai/...` here. Returns the `{data,meta,error}` envelope on 429 (NFR-051).

### Health & metrics (NFR-062)

Actuator is already on the classpath (Phase 1). `application.yml` exposes
`health,info`; add `metrics,prometheus` in production and put readiness/liveness behind
your orchestrator. DB connectivity is included in `/actuator/health` automatically.

---

## 6. Exit criteria (Phase 6)

- [ ] App installs as a PWA; **all Lighthouse PWA checks pass** (manifest, SW, installable).
- [ ] Offline: cached shell + static assets load; offline fallback shown for uncached
      navigations; API uses network-first with cache fallback.
- [ ] Dashboard shows today's doses (loggable inline), low-stock, and expiry.
- [ ] axe scan clean; keyboard-only + 200% zoom walkthrough pass; functional at 375px.
- [ ] Security headers present; `/auth` and `/ai` return 429 when hammered;
      `/actuator/health` is green.

## 7. Tests (Phase 6)

- Lighthouse CI: PWA + accessibility budgets (NFR-070).
- Service-worker offline test (Playwright offline mode) — shell + offline page render.
- axe automated scan across key pages; manual keyboard/contrast audit.
- Backend: security-header assertions; rate-limit integration test (11th `/auth` request
  in a minute → 429); `/actuator/health` returns UP.

Covers FR-100…103, NFR-010…015 / 070…072, SEC-P02…P05. This completes the MVP — see the
demo script in [docs/mvp-definition.md](../docs/mvp-definition.md) §7.
