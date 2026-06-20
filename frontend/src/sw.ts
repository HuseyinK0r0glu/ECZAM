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
