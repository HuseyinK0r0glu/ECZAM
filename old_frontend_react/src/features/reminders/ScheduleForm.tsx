import { useState } from "react";
import { Plus } from "lucide-react";
import { createSchedule, type FrequencyType } from "../../services/scheduleService";
import { Button, Field, Input } from "../../components/ui";
import { cn } from "../../utils/cn";

const DAYS = [["Pzt",1],["Sal",2],["Çar",3],["Per",4],["Cum",5],["Cmt",6],["Paz",7]] as const;

export default function ScheduleForm({ userMedicationId, onCreated }: {
  userMedicationId: string; onCreated: () => void;
}) {
  const [dosageAmount, setDosage] = useState(1);
  const [frequencyType, setType] = useState<FrequencyType>("daily");
  const [frequencyValue, setValue] = useState(2);
  const [times, setTimes] = useState<string[]>(["08:00"]);
  const [days, setDays] = useState<number[]>([]);

  async function submit() {
    await createSchedule(userMedicationId, {
      dosageAmount, frequencyType,
      frequencyValue: frequencyType === "interval" ? frequencyValue : undefined,
      scheduledTimes: times,
      daysOfWeek: frequencyType === "weekly" ? days : undefined,
    });
    onCreated();
  }

  return (
    <div className="space-y-4 rounded-2xl border border-line bg-zinc-50 p-4">
      <h3 className="text-xl font-semibold text-ink-strong">Yeni Program</h3>

      <Field label="Doz (adet)">
        <Input type="number" min={0} value={dosageAmount} onChange={(e) => setDosage(Number(e.target.value))} />
      </Field>

      <Field label="Sıklık">
        <select value={frequencyType} onChange={(e) => setType(e.target.value as FrequencyType)} className="input">
          <option value="daily">Her gün</option>
          <option value="weekly">Haftanın belirli günleri</option>
          <option value="interval">Her N günde bir</option>
        </select>
      </Field>

      {frequencyType === "interval" && (
        <Field label="Kaç günde bir">
          <Input type="number" min={1} value={frequencyValue} onChange={(e) => setValue(Number(e.target.value))} />
        </Field>
      )}

      {frequencyType === "weekly" && (
        <fieldset className="space-y-2">
          <legend className="text-base font-medium text-ink-strong">Günler</legend>
          <div className="flex flex-wrap gap-2">
            {DAYS.map(([label, n]) => (
              <button
                type="button"
                key={n}
                aria-pressed={days.includes(n)}
                onClick={() => setDays((d) => d.includes(n) ? d.filter((x) => x !== n) : [...d, n])}
                className={cn(
                  "min-h-[2.75rem] rounded-xl border px-4 text-base font-medium transition-colors",
                  days.includes(n)
                    ? "border-brand-700 bg-brand-700 text-white"
                    : "border-line bg-surface text-ink-strong hover:bg-zinc-100"
                )}
              >
                {label}
              </button>
            ))}
          </div>
        </fieldset>
      )}

      <div className="space-y-2">
        <span className="text-base font-medium text-ink-strong">Saatler</span>
        <div className="flex flex-wrap items-center gap-2">
          {times.map((t, i) => (
            <input
              key={i}
              type="time"
              value={t}
              onChange={(e) => setTimes((arr) => arr.map((x, j) => j === i ? e.target.value : x))}
              className="rounded-xl border border-line bg-surface px-3 py-2.5 text-lg"
            />
          ))}
          <Button type="button" variant="secondary" onClick={() => setTimes((a) => [...a, "20:00"])} className="min-h-0 px-3 py-2.5 text-base">
            <Plus className="h-4 w-4" aria-hidden /> saat
          </Button>
        </div>
      </div>

      <Button onClick={submit} block>Programı Kaydet</Button>
    </div>
  );
}
