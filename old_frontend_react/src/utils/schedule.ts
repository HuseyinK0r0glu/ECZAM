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
