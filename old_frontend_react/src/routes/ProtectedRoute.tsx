import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";
import DoseDeepLink from "../features/notifications/DoseDeepLink";
import AppShell from "../components/layout/AppShell";
import { Spinner } from "../components/ui";

export default function ProtectedRoute() {
  const { user, loading } = useAuth();
  if (loading) return <div className="grid min-h-screen place-items-center">{<Spinner />}</div>;
  if (!user) return <Navigate to="/login" replace />;
  return (
    <AppShell>
      <DoseDeepLink />
      <Outlet />
    </AppShell>
  );
}
