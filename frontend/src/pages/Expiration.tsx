import { useQuery } from "@tanstack/react-query";
import { motion } from "motion/react";
import { CalendarX2, ShieldCheck } from "lucide-react";
import { apiClient } from "../services/apiClient";
import type { ApiResponse } from "../types";
import type { InventoryItem } from "../services/inventoryService";
import { Badge, EmptyState, PageHeader, Spinner } from "../components/ui";
import { fadeUpContainer, fadeUpItem } from "../utils/motion";

const fetchList = (path: string) => async (): Promise<InventoryItem[]> => {
  const r = await apiClient.get<ApiResponse<InventoryItem[]>>(path);
  return r.data.data!;
};

export default function Expiration() {
  const soon = useQuery({ queryKey: ["expiring-soon"], queryFn: fetchList("/expiration/expiring-soon") });
  const expired = useQuery({ queryKey: ["expired"], queryFn: fetchList("/expiration/expired") });

  if (soon.isLoading || expired.isLoading) return <Spinner />;

  const expiredItems = expired.data ?? [];
  const soonItems = soon.data ?? [];
  const allClear = expiredItems.length === 0 && soonItems.length === 0;

  return (
    <main className="mx-auto max-w-3xl px-4 py-6 sm:px-6">
      <PageHeader title="Son Kullanma" subtitle="İlaçlarınızın son kullanma durumu" />

      {allClear ? (
        <EmptyState
          icon={<ShieldCheck className="h-10 w-10" aria-hidden />}
          title="Her şey yolunda"
          description="Süresi dolmuş veya yakında dolacak ilaç yok."
        />
      ) : (
        <motion.div variants={fadeUpContainer} initial="hidden" animate="show">
          <motion.section variants={fadeUpItem} className="mb-8" aria-labelledby="expired-h">
            <h2 id="expired-h" className="mb-3 flex items-center gap-2 text-2xl font-semibold tracking-tight text-rose-700">
              <CalendarX2 className="h-6 w-6" aria-hidden /> Süresi Dolmuş
            </h2>
            {expiredItems.length > 0 ? (
              <ul className="space-y-2">
                {expiredItems.map((i) => (
                  <li key={i.id} className="flex items-center justify-between gap-3 rounded-2xl border border-rose-200 bg-rose-50 p-4">
                    <span className="text-lg font-medium text-ink-strong">{i.medicationName}</span>
                    <Badge variant="danger">{i.expirationDate}</Badge>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="rounded-2xl border border-dashed border-line bg-surface p-4 text-lg text-ink-muted">
                Süresi dolmuş ilaç yok.
              </p>
            )}
          </motion.section>

          <motion.section variants={fadeUpItem} aria-labelledby="soon-h">
            <h2 id="soon-h" className="mb-3 flex items-center gap-2 text-2xl font-semibold tracking-tight text-orange-700">
              <CalendarX2 className="h-6 w-6" aria-hidden /> Yakında Dolacak
            </h2>
            {soonItems.length > 0 ? (
              <ul className="space-y-2">
                {soonItems.map((i) => (
                  <li key={i.id} className="flex items-center justify-between gap-3 rounded-2xl border border-orange-200 bg-orange-50 p-4">
                    <span className="text-lg font-medium text-ink-strong">{i.medicationName}</span>
                    <Badge variant="orange">{i.expirationDate}</Badge>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="rounded-2xl border border-dashed border-line bg-surface p-4 text-lg text-ink-muted">
                Yakında dolacak ilaç yok.
              </p>
            )}
          </motion.section>
        </motion.div>
      )}
    </main>
  );
}
