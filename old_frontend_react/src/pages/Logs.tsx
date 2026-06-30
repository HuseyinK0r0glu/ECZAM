import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { motion } from "motion/react";
import { Check, History } from "lucide-react";
import { listInventory } from "../services/inventoryService";
import { listLogs } from "../services/logService";
import { EmptyState, PageHeader } from "../components/ui";
import { fadeUpContainer, fadeUpItem } from "../utils/motion";

export default function Logs() {
  const { data: items } = useQuery({ queryKey: ["inventory"], queryFn: listInventory });
  const [selected, setSelected] = useState<string>("");
  const { data: logs } = useQuery({
    queryKey: ["logs", selected], queryFn: () => listLogs(selected), enabled: !!selected,
  });

  return (
    <main className="mx-auto max-w-3xl px-4 py-6 sm:px-6">
      <PageHeader title="Geçmiş" subtitle="Aldığınız dozların kaydı" />

      <select
        value={selected}
        onChange={(e) => setSelected(e.target.value)}
        className="input mb-5"
      >
        <option value="">İlaç seçin…</option>
        {items?.map((i) => <option key={i.id} value={i.id}>{i.medicationName}</option>)}
      </select>

      {!selected ? (
        <EmptyState
          icon={<History className="h-10 w-10" aria-hidden />}
          title="Bir ilaç seçin"
          description="Doz geçmişini görüntülemek için yukarıdan bir ilaç seçin."
        />
      ) : logs?.length === 0 ? (
        <EmptyState
          icon={<History className="h-10 w-10" aria-hidden />}
          title="Kayıt yok"
          description="Bu ilaç için henüz alınmış doz kaydı bulunmuyor."
        />
      ) : (
        <motion.ul
          variants={fadeUpContainer}
          initial="hidden"
          animate="show"
          className="space-y-2"
        >
          {logs?.map((l) => (
            <motion.li key={l.id} variants={fadeUpItem} className="card flex items-center gap-3 p-4">
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-emerald-50 text-emerald-700">
                <Check className="h-5 w-5" aria-hidden />
              </span>
              <span className="text-lg text-ink-strong">
                {new Date(l.takenAt).toLocaleString("tr-TR")} — {l.quantityUsed} alındı
              </span>
            </motion.li>
          ))}
        </motion.ul>
      )}
    </main>
  );
}
