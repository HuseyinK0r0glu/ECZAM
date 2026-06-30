import { useEffect, useState, type ReactNode } from "react";
import { Link, NavLink, useLocation, useNavigate } from "react-router-dom";
import { MotionConfig } from "motion/react";
import {
  ArrowLeft, Bot, CalendarClock, CalendarX2, Home, LogOut, Pill,
  PanelLeftClose, PanelLeftOpen,
} from "lucide-react";
import { useAuth } from "../../contexts/AuthContext";
import { cn } from "../../utils/cn";
import Logo from "../landing/Logo";

const NAV = [
  { to: "/dashboard", label: "Ana Sayfa", icon: Home, end: true },
  { to: "/inventory", label: "Envanter", icon: Pill, end: false },
  { to: "/schedules", label: "Programlar", icon: CalendarClock, end: false },
  { to: "/expiration", label: "Son Kullanma", icon: CalendarX2, end: false },
  { to: "/assistant", label: "Asistan", icon: Bot, end: false },
] as const;

const PRIMARY_PATHS = new Set<string>([...NAV.map((n) => n.to), "/logs"]);
const labelFor = (pathname: string) =>
  NAV.find((n) => (n.end ? pathname === n.to : pathname.startsWith(n.to)))?.label ?? "";

const COLLAPSE_KEY = "eczam.sidebar.collapsed";

function BrandMark({ collapsed }: { collapsed?: boolean }) {
  return (
    <Link to="/dashboard" className="flex items-center overflow-hidden" aria-label="ECZAM ana sayfa">
      <Logo wordmark={!collapsed} />
    </Link>
  );
}

function Sidebar({ collapsed, onToggle }: { collapsed: boolean; onToggle: () => void }) {
  const { user, logout } = useAuth();
  return (
    <aside
      className={cn(
        "fixed inset-y-0 left-0 z-30 hidden flex-col border-r border-line bg-surface/80 backdrop-blur-md transition-[width] duration-200 md:flex",
        collapsed ? "w-[4.75rem]" : "w-64"
      )}
    >
      <div className={cn("flex h-16 items-center px-3", collapsed ? "justify-center" : "justify-between")}>
        {!collapsed && <BrandMark />}
        <button
          onClick={onToggle}
          aria-label={collapsed ? "Menüyü genişlet" : "Menüyü daralt"}
          className="flex h-10 w-10 items-center justify-center rounded-xl text-ink-muted hover:bg-zinc-100 hover:text-ink-strong"
        >
          {collapsed ? <PanelLeftOpen className="h-5 w-5" aria-hidden /> : <PanelLeftClose className="h-5 w-5" aria-hidden />}
        </button>
      </div>

      <nav aria-label="Birincil" className="flex-1 space-y-1 px-3 py-2">
        {NAV.map(({ to, label, icon: Icon, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            title={collapsed ? label : undefined}
            className={({ isActive }) =>
              cn(
                "relative flex items-center gap-3 rounded-xl px-3 py-2.5 text-base font-medium transition-colors",
                collapsed && "justify-center px-0",
                isActive
                  ? "bg-brand-50 text-brand-800"
                  : "text-ink-muted hover:bg-zinc-100 hover:text-ink-strong"
              )
            }
          >
            {({ isActive }) => (
              <>
                {isActive && !collapsed && (
                  <span className="absolute left-0 top-1/2 h-6 w-1 -tranzinc-y-1/2 rounded-r-full bg-brand-700" aria-hidden />
                )}
                <Icon className="h-5 w-5 shrink-0" aria-hidden />
                {!collapsed && <span className="truncate">{label}</span>}
              </>
            )}
          </NavLink>
        ))}
      </nav>

      <div className="border-t border-line p-3">
        <div className={cn("flex items-center gap-3", collapsed && "justify-center")}>
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-100 text-base font-semibold text-brand-800">
            {(user?.displayName || user?.email || "?").charAt(0).toUpperCase()}
          </span>
          {!collapsed && (
            <div className="min-w-0 flex-1">
              <p className="truncate text-base font-medium text-ink-strong">{user?.displayName || user?.email}</p>
            </div>
          )}
          <button
            onClick={logout}
            aria-label="Çıkış"
            title="Çıkış"
            className={cn(
              "flex h-9 w-9 shrink-0 items-center justify-center rounded-xl text-ink-muted hover:bg-zinc-100 hover:text-ink-strong",
              collapsed && "hidden"
            )}
          >
            <LogOut className="h-5 w-5" aria-hidden />
          </button>
        </div>
      </div>
    </aside>
  );
}

function MobileTopBar() {
  const { logout } = useAuth();
  const { pathname } = useLocation();
  const nav = useNavigate();
  const showBack = !PRIMARY_PATHS.has(pathname);
  return (
    <header className="sticky top-0 z-20 flex h-16 items-center gap-2 border-b border-line bg-surface/80 px-4 backdrop-blur-md md:hidden">
      {showBack ? (
        <button
          onClick={() => nav(-1)}
          aria-label="Geri"
          className="-ml-2 flex h-11 w-11 items-center justify-center rounded-xl text-ink-strong hover:bg-zinc-100"
        >
          <ArrowLeft className="h-6 w-6" aria-hidden />
        </button>
      ) : (
        <BrandMark />
      )}
      <button
        onClick={logout}
        aria-label="Çıkış"
        className="ml-auto flex h-11 w-11 items-center justify-center rounded-xl text-ink-muted hover:bg-zinc-100 hover:text-ink-strong"
      >
        <LogOut className="h-5 w-5" aria-hidden />
      </button>
    </header>
  );
}

function DesktopBackBar() {
  const { pathname } = useLocation();
  const nav = useNavigate();
  if (PRIMARY_PATHS.has(pathname)) return null;
  return (
    <div className="sticky top-0 z-20 hidden h-14 items-center gap-2 border-b border-line bg-surface/70 px-4 backdrop-blur-md md:flex">
      <button
        onClick={() => nav(-1)}
        className="flex items-center gap-2 rounded-xl px-3 py-2 text-base font-medium text-ink-muted hover:bg-zinc-100 hover:text-ink-strong"
      >
        <ArrowLeft className="h-5 w-5" aria-hidden /> Geri
      </button>
      <span className="text-base text-ink-muted">{labelFor(pathname)}</span>
    </div>
  );
}

function BottomNav() {
  return (
    <nav
      aria-label="Ana gezinme"
      className="fixed inset-x-0 bottom-0 z-20 border-t border-line bg-surface/95 shadow-nav backdrop-blur-md md:hidden"
      style={{ paddingBottom: "env(safe-area-inset-bottom)" }}
    >
      <ul className="mx-auto flex max-w-3xl">
        {NAV.map(({ to, label, icon: Icon, end }) => (
          <li key={to} className="flex-1">
            <NavLink
              to={to}
              end={end}
              className={({ isActive }) =>
                cn("flex flex-col items-center gap-1 py-2 text-xs font-medium", isActive ? "text-brand-700" : "text-ink-muted")
              }
            >
              {({ isActive }) => (
                <>
                  <span className={cn("flex h-9 w-12 items-center justify-center rounded-full transition-colors", isActive && "bg-brand-50")}>
                    <Icon className="h-6 w-6" aria-hidden />
                  </span>
                  <span>{label}</span>
                </>
              )}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}

export default function AppShell({ children }: { children: ReactNode }) {
  const [collapsed, setCollapsed] = useState<boolean>(() => {
    try { return localStorage.getItem(COLLAPSE_KEY) === "1"; } catch { return false; }
  });
  useEffect(() => {
    try { localStorage.setItem(COLLAPSE_KEY, collapsed ? "1" : "0"); } catch { /* ignore */ }
  }, [collapsed]);

  return (
    <MotionConfig reducedMotion="user">
      <div className="min-h-screen">
        <Sidebar collapsed={collapsed} onToggle={() => setCollapsed((c) => !c)} />
        <div className={cn("pb-28 transition-[padding] duration-200 md:pb-0", collapsed ? "md:pl-[4.75rem]" : "md:pl-64")}>
          <MobileTopBar />
          <DesktopBackBar />
          {children}
          <BottomNav />
        </div>
      </div>
    </MotionConfig>
  );
}
