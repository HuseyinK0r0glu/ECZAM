import axios, { AxiosError } from "axios";

const BASE = import.meta.env.VITE_API_URL ?? "http://localhost:8080/api/v1";

export const apiClient = axios.create({ baseURL: BASE, headers: { "Content-Type": "application/json" } });

const ACCESS_KEY = "eczam.access";
const REFRESH_KEY = "eczam.refresh";

export const tokenStore = {
  access: () => localStorage.getItem(ACCESS_KEY),
  refresh: () => localStorage.getItem(REFRESH_KEY),
  set: (access: string, refresh: string) => {
    localStorage.setItem(ACCESS_KEY, access);
    localStorage.setItem(REFRESH_KEY, refresh);
  },
  clear: () => { localStorage.removeItem(ACCESS_KEY); localStorage.removeItem(REFRESH_KEY); },
};

apiClient.interceptors.request.use((config) => {
  const t = tokenStore.access();
  if (t) config.headers.Authorization = `Bearer ${t}`;
  return config;
});

// On 401, try one refresh, then replay; otherwise clear and bubble up.
let refreshing: Promise<string> | null = null;
apiClient.interceptors.response.use(
  (r) => r,
  async (error: AxiosError) => {
    const original = error.config!;
    const refresh = tokenStore.refresh();
    if (error.response?.status === 401 && refresh && !(original as any)._retry) {
      (original as any)._retry = true;
      try {
        refreshing ??= apiClient
          .post<ApiAuth>("/auth/refresh", { refreshToken: refresh })
          .then((res) => {
            const d = res.data.data!;
            tokenStore.set(d.accessToken, d.refreshToken);
            return d.accessToken;
          })
          .finally(() => { refreshing = null; });
        const newAccess = await refreshing;
        original.headers!.Authorization = `Bearer ${newAccess}`;
        return apiClient(original);
      } catch {
        tokenStore.clear();
      }
    }
    return Promise.reject(error);
  }
);

interface ApiAuth { data: { accessToken: string; refreshToken: string } | null; }
