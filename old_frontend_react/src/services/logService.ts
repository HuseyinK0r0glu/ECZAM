import { apiClient } from "./apiClient";
import type { ApiResponse } from "../types";

export interface LogResult { log: { id: string; takenAt: string }; newQuantity: number; lowStock: boolean; }
export interface LogView { id: string; takenAt: string; quantityUsed: number; notes?: string; }

export async function logDose(userMedicationId: string, quantityUsed: number, scheduleId?: string): Promise<LogResult> {
  const r = await apiClient.post<ApiResponse<LogResult>>("/medication-logs",
    { userMedicationId, quantityUsed, scheduleId });
  return r.data.data!;
}
export async function listLogs(userMedicationId: string): Promise<LogView[]> {
  const r = await apiClient.get<ApiResponse<LogView[]>>("/medication-logs", { params: { userMedicationId } });
  return r.data.data!;
}
