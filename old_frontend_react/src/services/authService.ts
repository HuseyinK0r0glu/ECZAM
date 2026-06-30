import { apiClient, tokenStore } from "./apiClient";
import type { ApiResponse, AuthResponse, UserSummary } from "../types";

export async function register(email: string, password: string, displayName?: string) {
  const res = await apiClient.post<ApiResponse<AuthResponse>>("/auth/register", { email, password, displayName });
  return persist(res.data.data!);
}

export async function login(email: string, password: string) {
  const res = await apiClient.post<ApiResponse<AuthResponse>>("/auth/login", { email, password });
  return persist(res.data.data!);
}

export async function fetchMe(): Promise<UserSummary> {
  const res = await apiClient.get<ApiResponse<UserSummary>>("/users/me");
  return res.data.data!;
}

export function logout() { tokenStore.clear(); }

function persist(auth: AuthResponse): UserSummary {
  tokenStore.set(auth.accessToken, auth.refreshToken);
  return auth.user;
}
