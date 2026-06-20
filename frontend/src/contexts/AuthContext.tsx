import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { fetchMe, login as svcLogin, logout as svcLogout, register as svcRegister } from "../services/authService";
import { tokenStore } from "../services/apiClient";
import type { UserSummary } from "../types";

interface AuthState {
  user: UserSummary | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, displayName?: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserSummary | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!tokenStore.access()) { setLoading(false); return; }
    fetchMe().then(setUser).catch(() => tokenStore.clear()).finally(() => setLoading(false));
  }, []);

  const value: AuthState = {
    user,
    loading,
    login: async (e, p) => setUser(await svcLogin(e, p)),
    register: async (e, p, d) => setUser(await svcRegister(e, p, d)),
    logout: () => { svcLogout(); setUser(null); },
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
