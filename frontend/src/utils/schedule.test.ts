import { describe, it, expect } from "vitest";
import { isDueToday } from "./schedule";
import type { ScheduleView } from "../services/scheduleService";

// Midnight UTC so the toISOString() date comparisons are timezone-stable.
const TODAY = new Date("2026-06-17T00:00:00Z");

function schedule(over: Partial<ScheduleView> = {}): ScheduleView {
  return {
    id: "s1", userMedicationId: "um1", medicationName: "Test",
    dosageAmount: 1, frequencyType: "daily", scheduledTimes: ["08:00"],
    active: true, startsOn: "2026-06-01",
    ...over,
  };
}

describe("isDueToday", () => {
  it("daily schedule within range is due", () => {
    expect(isDueToday(schedule(), TODAY)).toBe(true);
  });

  it("paused schedule is never due", () => {
    expect(isDueToday(schedule({ active: false }), TODAY)).toBe(false);
  });

  it("not due before startsOn or after endsOn", () => {
    expect(isDueToday(schedule({ startsOn: "2026-06-20" }), TODAY)).toBe(false);
    expect(isDueToday(schedule({ endsOn: "2026-06-10" }), TODAY)).toBe(false);
  });

  it("weekly schedule is due only on listed ISO days", () => {
    const isoDow = TODAY.getDay() === 0 ? 7 : TODAY.getDay();
    const other = (isoDow % 7) + 1;
    expect(isDueToday(schedule({ frequencyType: "weekly", daysOfWeek: [isoDow] }), TODAY)).toBe(true);
    expect(isDueToday(schedule({ frequencyType: "weekly", daysOfWeek: [other] }), TODAY)).toBe(false);
  });

  it("interval schedule is due every N days from the start", () => {
    expect(isDueToday(schedule({ frequencyType: "interval", frequencyValue: 2, startsOn: "2026-06-15" }), TODAY)).toBe(true);
    expect(isDueToday(schedule({ frequencyType: "interval", frequencyValue: 2, startsOn: "2026-06-16" }), TODAY)).toBe(false);
  });
});
