import { Bell } from "lucide-react";
import { useNotifications } from "../../hooks/useNotifications";
import { Button } from "../../components/ui";

export default function EnablePushPrompt() {
  const { enabled, enable } = useNotifications();
  if (enabled) return null;
  return (
    <div
      role="region"
      aria-label="Bildirim izni"
      className="card flex h-full flex-col gap-3 bg-brand-50 p-5 ring-brand-200"
    >
      <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-brand-100 text-brand-700">
        <Bell className="h-6 w-6" aria-hidden />
      </span>
      <div className="flex-1">
        <p className="text-lg font-semibold text-ink-strong">Hatırlatmaları açın</p>
        <p className="mt-1 text-base text-ink">İlaç zamanı geldiğinde bildirim alın.</p>
      </div>
      <Button onClick={enable} block>
        Bildirimleri Aç
      </Button>
    </div>
  );
}
