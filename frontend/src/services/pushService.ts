import { apiClient } from "./apiClient";
import type { ApiResponse } from "../types";

function urlBase64ToUint8Array(base64: string) {
  const padding = "=".repeat((4 - (base64.length % 4)) % 4);
  const b64 = (base64 + padding).replace(/-/g, "+").replace(/_/g, "/");
  const raw = atob(b64);
  const out = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
  return out;
}

export async function getVapidPublicKey(): Promise<string> {
  const r = await apiClient.get<ApiResponse<{ publicKey: string }>>("/push/vapid-public-key");
  return r.data.data!.publicKey;
}

export async function registerPush(): Promise<boolean> {
  if (!("serviceWorker" in navigator) || !("PushManager" in window)) return false;
  const permission = await Notification.requestPermission();
  if (permission !== "granted") return false;

  // The service worker is registered by vite-plugin-pwa at app startup; wait for it.
  const reg = await navigator.serviceWorker.ready;
  const key = import.meta.env.VITE_VAPID_PUBLIC_KEY || (await getVapidPublicKey());
  if (!key) return false;
  const sub = await reg.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: urlBase64ToUint8Array(key),
  });
  const json = sub.toJSON();
  await apiClient.post("/push/subscriptions", {
    endpoint: json.endpoint,
    keys: { p256dh: json.keys!.p256dh, auth: json.keys!.auth },
    userAgent: navigator.userAgent,
  });
  return true;
}

export { urlBase64ToUint8Array };
