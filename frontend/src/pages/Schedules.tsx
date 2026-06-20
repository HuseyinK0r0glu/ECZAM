import { useQuery, useQueryClient } from "@tanstack/react-query";
import { motion } from "motion/react";
import { CalendarClock, Pause, Play, Trash2 } from "lucide-react";
import { listSchedules, pauseSchedule, resumeSchedule, deleteSchedule } from "../services/scheduleService";
import { Badge, Button, EmptyState, PageHeader, Spinner } from "../components/ui";
import { fadeUpContainer, fadeUpItem } from "../utils/motion";

export default function Schedules() {
  const qc = useQueryClient();
  const { data, isLoading } = useQuery({ queryKey: ["schedules"], queryFn: listSchedules });
  if (isLoading) return <Spinner />;
  const refresh = () => qc.invalidateQueries({ queryKey: ["schedules"] });

  return (
    <main className="mx-auto max-w-3xl px-4 py-6 sm:px-6">
      <PageHeader title="Programlar" subtitle="İlaç hatırlatma planlarınız" />

      {data?.length === 0 ? (
        <EmptyState
          icon={<CalendarClock className="h-10 w-10" aria-hidden />}
          title="Henüz program yok"
          description="Bir ilacın detay sayfasından doz programı ekleyebilirsiniz."
        />
      ) : (
        <motion.ul
          variants={fadeUpContainer}
          initial="hidden"
          animate="show"
          className="space-y-3"
        >
          {data?.map((s) => (
            <motion.li key={s.id} variants={fadeUpItem} className="card flex items-center justify-between gap-4 p-5">
              <div className="min-w-0">
                <span className="text-xl font-semibold text-ink-strong">{s.medicationName}</span>
                <p className="mt-1 text-lg text-ink-muted">
                  {s.dosageAmount} · {s.scheduledTimes.join(", ")}
                  {!s.active && <Badge variant="neutral" className="ml-2">Duraklatıldı</Badge>}
                </p>
              </div>
              <div className="flex shrink-0 gap-2">
                <Button
                  variant="secondary"
                  onClick={async () => { await (s.active ? pauseSchedule(s.id) : resumeSchedule(s.id)); refresh(); }}
                  className="min-h-0 px-3 py-2 text-base"
                >
                  {s.active ? <><Pause className="h-4 w-4" aria-hidden /> Duraklat</> : <><Play className="h-4 w-4" aria-hidden /> Devam</>}
                </Button>
                <Button
                  variant="danger"
                  onClick={async () => { await deleteSchedule(s.id); refresh(); }}
                  className="min-h-0 px-3 py-2 text-base"
                  aria-label="Programı sil"
                >
                  <Trash2 className="h-4 w-4" aria-hidden /> Sil
                </Button>
              </div>
            </motion.li>
          ))}
        </motion.ul>
      )}
    </main>
  );
}
