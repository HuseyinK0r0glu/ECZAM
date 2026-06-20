import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Check } from "lucide-react";
import { logDose } from "../../services/logService";
import { Button } from "../../components/ui";

export default function LogDoseButton({ userMedicationId, amount = 1, scheduleId }: {
  userMedicationId: string; amount?: number; scheduleId?: string;
}) {
  const qc = useQueryClient();
  const [msg, setMsg] = useState<string | null>(null);

  async function onClick() {
    try {
      const res = await logDose(userMedicationId, amount, scheduleId);
      setMsg(`Alındı. Kalan: ${res.newQuantity}`);
      qc.invalidateQueries({ queryKey: ["inventory"] });
      qc.invalidateQueries({ queryKey: ["logs", userMedicationId] });
    } catch (e: any) {
      setMsg(e?.response?.data?.error?.code === "INSUFFICIENT_STOCK" ? "Yetersiz stok." : "Hata.");
    }
  }

  return (
    <div className="inline-flex flex-col items-end gap-1">
      <Button variant="success" onClick={onClick} className="whitespace-nowrap">
        <Check className="h-5 w-5" aria-hidden /> Aldım
      </Button>
      {msg && (
        <span role="status" className="text-base font-medium text-ink-muted">
          {msg}
        </span>
      )}
    </div>
  );
}
