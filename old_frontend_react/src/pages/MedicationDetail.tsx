import { useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Pause, Play, Plus, Search, Trash2 } from "lucide-react";
import { getMedication, searchLeaflet, type LeafletHit } from "../services/medicationService";
import { listSchedulesForMedication, pauseSchedule, resumeSchedule, deleteSchedule } from "../services/scheduleService";
import LogDoseButton from "../features/reminders/LogDoseButton";
import ScheduleForm from "../features/reminders/ScheduleForm";
import TtsControlBar from "../features/medications/TtsControlBar";
import { Badge, Button, Card, Input, PageHeader, Spinner } from "../components/ui";

const SECTION_LABELS: Record<string, string> = {
  dosage: "Doz", side_effects: "Yan etkiler", contraindications: "Kullanılmaması gereken durumlar",
  storage: "Saklama", interactions: "Etkileşimler", missed_dose: "Doz atlanırsa",
};

export default function MedicationDetail() {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const umId = searchParams.get("um");
  const qc = useQueryClient();
  const { data: med, isLoading } = useQuery({ queryKey: ["medication", id], queryFn: () => getMedication(id!) });
  const [q, setQ] = useState("");
  const [hits, setHits] = useState<LeafletHit[] | null>(null);
  const [showForm, setShowForm] = useState(false);
  const { data: schedules } = useQuery({
    queryKey: ["schedules", umId], queryFn: () => listSchedulesForMedication(umId!), enabled: !!umId,
  });

  if (isLoading || !med) return <Spinner />;

  async function onSearch() { if (q.trim()) setHits(await searchLeaflet(id!, q)); }
  const refreshSchedules = () => qc.invalidateQueries({ queryKey: ["schedules", umId] });
  const sections = med.leafletSections ?? {};

  return (
    <main className="mx-auto max-w-3xl px-4 py-6 pb-32 sm:px-6">
      <PageHeader
        title={med.name}
        subtitle={med.strength ? `${med.strength} · ${med.form}` : undefined}
      />

      {umId && (
        <Card className="mb-6 space-y-4">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-xl font-semibold tracking-tight text-ink-strong">Doz &amp; Program</h2>
            <LogDoseButton userMedicationId={umId} />
          </div>

          <ul className="space-y-2">
            {schedules?.map((s) => (
              <li key={s.id} className="flex items-center justify-between gap-3 rounded-xl bg-zinc-50 p-3">
                <span className="text-lg text-ink-strong">
                  {s.dosageAmount} · {s.scheduledTimes.join(", ")}
                  {!s.active && <Badge variant="neutral" className="ml-2">Duraklatıldı</Badge>}
                </span>
                <span className="flex shrink-0 gap-2">
                  <Button
                    variant="secondary"
                    onClick={async () => { await (s.active ? pauseSchedule(s.id) : resumeSchedule(s.id)); refreshSchedules(); }}
                    className="min-h-0 px-3 py-2 text-base"
                  >
                    {s.active ? <><Pause className="h-4 w-4" aria-hidden /> Duraklat</> : <><Play className="h-4 w-4" aria-hidden /> Devam</>}
                  </Button>
                  <Button
                    variant="danger"
                    onClick={async () => { await deleteSchedule(s.id); refreshSchedules(); }}
                    className="min-h-0 px-3 py-2 text-base"
                    aria-label="Programı sil"
                  >
                    <Trash2 className="h-4 w-4" aria-hidden /> Sil
                  </Button>
                </span>
              </li>
            ))}
            {schedules?.length === 0 && <p className="text-ink-muted">Henüz program yok.</p>}
          </ul>

          {showForm
            ? <ScheduleForm userMedicationId={umId} onCreated={() => { setShowForm(false); refreshSchedules(); }} />
            : <Button onClick={() => setShowForm(true)}><Plus className="h-5 w-5" aria-hidden /> Program ekle</Button>}
        </Card>
      )}

      <div className="mb-6 flex gap-2">
        <Input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Prospektüste ara…"
               onKeyDown={(e) => e.key === "Enter" && onSearch()} />
        <Button onClick={onSearch} aria-label="Ara" className="shrink-0">
          <Search className="h-5 w-5" aria-hidden /> Ara
        </Button>
      </div>

      {hits && (
        <section className="mb-6">
          <h2 className="mb-2 text-xl font-semibold tracking-tight text-ink-strong">Arama sonuçları</h2>
          {hits.length === 0 ? <p className="text-ink-muted">Sonuç yok.</p> :
            <ul className="space-y-2">{hits.map((h, i) =>
              <li key={i} className="rounded-xl border border-amber-200 bg-amber-50 p-3 text-ink">
                <strong className="text-ink-strong">{SECTION_LABELS[h.section] ?? h.section}:</strong> {h.snippet}
              </li>)}</ul>}
        </section>
      )}

      <section className="space-y-3">
        {Object.entries(SECTION_LABELS).map(([key, label]) => {
          const text = (sections as Record<string, string | undefined>)[key];
          if (!text) return null;
          return (
            <details key={key} className="card overflow-hidden p-0" open>
              <summary className="flex cursor-pointer items-center justify-between p-4 text-xl font-semibold text-ink-strong">
                {label}
              </summary>
              <p className="whitespace-pre-line border-t border-line p-4 text-lg text-ink">{text}</p>
            </details>
          );
        })}
      </section>

      {med.leafletSections && Object.values(med.leafletSections).some(Boolean) && (
        <TtsControlBar sections={med.leafletSections} />
      )}
    </main>
  );
}
