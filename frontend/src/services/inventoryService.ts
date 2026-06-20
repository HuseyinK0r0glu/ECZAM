import { apiClient } from "./apiClient";
import type { ApiResponse } from "../types";

export type ExpiryStatus = "OK" | "EXPIRING_SOON" | "EXPIRED";
export interface InventoryItem {
  id: string; medicationId: string; medicationName: string; strength?: string; form?: string;
  quantity: number; unit: string; expirationDate?: string; notes?: string;
  lowStock: boolean; expiryStatus: ExpiryStatus;
}

export async function listInventory(): Promise<InventoryItem[]> {
  const r = await apiClient.get<ApiResponse<InventoryItem[]>>("/user-medications");
  return r.data.data!;
}
export async function addInventory(payload: {
  medicationId: string; quantity: number; unit?: string; expirationDate?: string; notes?: string;
}): Promise<InventoryItem> {
  const r = await apiClient.post<ApiResponse<InventoryItem>>("/user-medications", payload);
  return r.data.data!;
}
export async function updateInventory(id: string, payload: Partial<InventoryItem>) {
  const r = await apiClient.patch<ApiResponse<InventoryItem>>(`/user-medications/${id}`, payload);
  return r.data.data!;
}
export async function deleteInventory(id: string): Promise<void> {
  await apiClient.delete(`/user-medications/${id}`);
}
