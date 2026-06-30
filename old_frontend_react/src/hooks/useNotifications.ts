import { useState } from "react";
import { registerPush } from "../services/pushService";

const permissionGranted = () =>
  typeof Notification !== "undefined" && Notification.permission === "granted";

export function useNotifications() {
  const [enabled, setEnabled] = useState(permissionGranted());
  const [asked, setAsked] = useState(false);

  async function enable() {
    setAsked(true);
    const ok = await registerPush();
    setEnabled(ok);
    return ok;
  }

  // The service worker is registered by vite-plugin-pwa (registerType: autoUpdate);
  // no manual registration needed here.

  return { enabled, asked, enable };
}
