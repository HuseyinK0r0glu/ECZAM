import { apiClient } from "./apiClient";
import type { ApiResponse } from "../types";

export interface LeafletSections {
  dosage?: string; side_effects?: string; contraindications?: string;
  storage?: string; interactions?: string; missed_dose?: string;
}
export interface MedicationDetail {
  id: string; name: string; genericName?: string; manufacturer?: string;
  barcode?: string; form?: string; strength?: string;
  leafletSections?: LeafletSections; vectorIndexed: boolean;
}
export interface LeafletHit { section: string; snippet: string; }

export async function getByBarcode(code: string): Promise<MedicationDetail> {
  const r = await apiClient.get<ApiResponse<MedicationDetail>>(`/medications/barcode/${encodeURIComponent(code)}`);
  return r.data.data!;
}
export async function createMedication(payload: Partial<MedicationDetail> & { name: string }) {
  const r = await apiClient.post<ApiResponse<MedicationDetail>>("/medications", payload);
  return r.data.data!;
}
export async function getMedication(id: string): Promise<MedicationDetail> {
  const r = await apiClient.get<ApiResponse<MedicationDetail>>(`/medications/${id}`);
  return r.data.data!;
}
export async function searchLeaflet(id: string, q: string): Promise<LeafletHit[]> {
  const r = await apiClient.get<ApiResponse<{ hits: LeafletHit[] }>>(`/medications/${id}/leaflet/search`, { params: { q } });
  return r.data.data!.hits;
}
