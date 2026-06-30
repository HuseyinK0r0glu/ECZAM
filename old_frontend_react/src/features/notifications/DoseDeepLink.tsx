import { useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { logDose } from "../../services/logService";

/**
 * Handles the "✓ Aldım" notification deep link: the service worker opens
 * `/?logDose=<userMedicationId>&scheduleId=<id>`; we log the dose with the
 * signed-in user's token, then strip the params.
 */
export default function DoseDeepLink() {
  const [params, setParams] = useSearchParams();
  const qc = useQueryClient();

  useEffect(() => {
    const um = params.get("logDose");
    if (!um) return;
    logDose(um, 1, params.get("scheduleId") || undefined)
      .then(() => qc.invalidateQueries({ queryKey: ["inventory"] }))
      .catch(() => {})
      .finally(() => {
        params.delete("logDose");
        params.delete("scheduleId");
        setParams(params, { replace: true });
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params, setParams]);

  return null;
}
