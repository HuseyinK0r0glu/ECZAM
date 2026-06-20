import { apiClient } from "./apiClient";
import type { ApiResponse } from "../types";

export type FrequencyType = "daily" | "weekly" | "interval";
export interface ScheduleView {
  id: string; userMedicationId: string; medicationName: string;
  dosageAmount: number; frequencyType: FrequencyType; frequencyValue?: number;
  scheduledTimes: string[]; daysOfWeek?: number[];
  active: boolean; startsOn: string; endsOn?: string;
}

export async function listSchedules(): Promise<ScheduleView[]> {
  const r = await apiClient.get<ApiResponse<ScheduleView[]>>("/schedules");
  return r.data.data!;
}
export async function listSchedulesForMedication(umId: string): Promise<ScheduleView[]> {
  const r = await apiClient.get<ApiResponse<ScheduleView[]>>(`/user-medications/${umId}/schedules`);
  return r.data.data!;
}
export async function createSchedule(umId: string, payload: {
  dosageAmount: number; frequencyType: FrequencyType; frequencyValue?: number;
  scheduledTimes: string[]; daysOfWeek?: number[]; startsOn?: string; endsOn?: string;
}) {
  const r = await apiClient.post<ApiResponse<ScheduleView>>(`/user-medications/${umId}/schedules`, payload);
  return r.data.data!;
}
export const pauseSchedule  = (id: string) => apiClient.post(`/schedules/${id}/pause`);
export const resumeSchedule = (id: string) => apiClient.post(`/schedules/${id}/resume`);
export const deleteSchedule = (id: string) => apiClient.delete(`/schedules/${id}`);
