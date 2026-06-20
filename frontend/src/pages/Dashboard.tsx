import { type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { motion } from "motion/react";
import {
  AlertTriangle, BellRing, CalendarCheck, CalendarClock, Check, ChevronRight, History, Pill, Plus,
} from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import { useNotifications } from "../hooks/useNotifications";
import { listInventory } from "../services/inventoryService";
import { listSchedules } from "../services/scheduleService";
import { apiClient } from "../services/apiClient";
import type { ApiResponse } from "../types";
import type { InventoryItem } from "../services/inventoryService";
import { isDueToday } from "../utils/schedule";
import LogDoseButton from "../features/reminders/LogDoseButton";
import EnablePushPrompt from "../features/notifications/EnablePushPrompt";
import { Badge, buttonVariants, PageHeader } from "../components/ui";
import { cn } from "../utils/cn";
import { fadeUpContainer, fadeUpItem } from "../utils/motion";

function BentoCard({ title, icon, accent, action, className, children }: {
  title: string; icon: ReactNode; accent: string; action?: ReactNode; className?: string; children: ReactNode;
}) {
  return (
    <section className={cn("card flex flex-col p-5", className)} aria-label={title}>
      <div className="mb-3 flex items-center justify-between gap-2">
        <h2 className="flex items-center gap-2 text-lg font-semibold tracking-tight text-ink-strong">
          <span className={cn("flex h-8 w-8 items-center justify-center rounded-lg", accent)}>{icon}</span>
          {title}
        </h2>
        {action}
      </div>
      <div className="flex-1">{children}</div>
    </section>
  );
}

function MiniEmpty({ icon, text }: { icon: ReactNode; text: string }) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-2 py-5 text-center">
      <span className="flex h-11 w-11 items-center justify-center rounded-full bg-emerald-100 text-emerald-600 ring-[6px] ring-emerald-50">{icon}</span>
      <p className="text-base text-ink-muted">{text}</p>
    </div>
  );
}

export default function Dashboard() {
  const { user } = useAuth();
  const { enabled: pushEnabled } = useNotifications();
  const schedules = useQuery({ queryKey: ["schedules"], queryFn: listSchedules });
  const inventory = useQuery({ queryKey: ["inventory"], queryFn: listInventory });
  const expiring = useQuery({
    queryKey: ["expiring-soon"],
    queryFn: async () => (await apiClient.get<ApiResponse<InventoryItem[]>>("/expiration/expiring-soon")).data.data!,
  });

  const today = (schedules.data ?? []).filter((s) => isDueToday(s));
  const lowStock = (inventory.data ?? []).filter((i) => i.lowStock);
  const soon = expiring.data ?? [];

  const todayLabel = new Date().toLocaleDateString("tr-TR", { weekday: "long", day: "numeric", month: "long" });

  return (
    <main className="mx-auto max-w-5xl px-4 py-6 sm:px-6">
      <PageHeader title={`Merhaba${user?.displayName ? `, ${user.displayName}` : ""}`} subtitle={todayLabel} />

      <motion.div
        variants={fadeUpContainer}
        initial="hidden"
        animate="show"
        className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-6"
      >
        {/* Today's doses — primary, widest cell */}
        <motion.div variants={fadeUpItem} className="sm:col-span-2 lg:col-span-4">
        <BentoCard
          title="Bugünün Dozları"
          icon={<CalendarClock className="h-5 w-5" aria-hidden />}
          accent="bg-brand-50 text-brand-700"
          className="h-full"
        >
          {today.length > 0 ? (
            <ul className="-m-1 divide-y divide-line">
              {today.map((s) => (
                <li key={s.id} className="flex items-center justify-between gap-3 p-3">
                  <div className="min-w-0">
                    <p className="truncate text-lg font-semibold text-ink-strong">{s.medicationName}</p>
                    <p className="text-base text-ink-muted">{s.scheduledTimes.join(", ")}</p>
                  </div>
                  <LogDoseButton userMedicationId={s.userMedicationId} amount={s.dosageAmount} scheduleId={s.id} />
                </li>
              ))}
            </ul>
          ) : (
            <div className="flex h-full flex-col items-center justify-center gap-3 py-8 text-center">
              <span className="flex h-16 w-16 items-center justify-center rounded-full bg-brand-100 text-brand-700 ring-8 ring-brand-50">
                <CalendarCheck className="h-8 w-8" aria-hidden />
              </span>
              <div>
                <p className="text-lg font-semibold tracking-tight text-ink-strong">Bugün için doz yok</p>
                <p className="mt-1 text-base text-ink-muted">Planlanmış bir dozunuz yok. Keyfinize bakın!</p>
              </div>
            </div>
          )}
        </BentoCard>
        </motion.div>

        {/* Quick actions */}
        <motion.section
          variants={fadeUpItem}
          aria-label="Hızlı işlemler"
          className="card flex h-full flex-col gap-3 p-5 sm:col-span-1 lg:col-span-2"
        >
          <h2 className="flex items-center gap-2 text-lg font-semibold tracking-tight text-ink-strong">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-50 text-brand-700">
              <Plus className="h-5 w-5" aria-hidden />
            </span>
            Hızlı İşlemler
          </h2>
          <Link to="/inventory/add" className={cn(buttonVariants(), "w-full justify-start")}>
            <Pill className="h-5 w-5" aria-hidden /> İlaç Ekle
          </Link>
          <Link to="/schedules" className={cn(buttonVariants({ variant: "secondary" }), "w-full justify-start")}>
            <CalendarClock className="h-5 w-5 text-brand-700" aria-hidden /> Programlar
          </Link>
          <Link to="/logs" className={cn(buttonVariants({ variant: "secondary" }), "w-full justify-start")}>
            <History className="h-5 w-5 text-brand-700" aria-hidden /> Geçmiş
          </Link>
        </motion.section>

        {/* Notifications */}
        <motion.div variants={fadeUpItem} className="sm:col-span-1 lg:col-span-2">
          {pushEnabled ? (
            <BentoCard
              title="Bildirimler"
              icon={<BellRing className="h-5 w-5" aria-hidden />}
              accent="bg-emerald-50 text-emerald-600"
              className="h-full"
            >
              <MiniEmpty icon={<Check className="h-5 w-5" aria-hidden />} text="Hatırlatmalar açık." />
            </BentoCard>
          ) : (
            <EnablePushPrompt />
          )}
        </motion.div>

        {/* Low stock */}
        <motion.div variants={fadeUpItem} className="sm:col-span-1 lg:col-span-2">
        <BentoCard
          title="Azalan Stok"
          icon={<AlertTriangle className="h-5 w-5" aria-hidden />}
          accent="bg-amber-50 text-amber-600"
          action={lowStock.length > 0 ? <Badge variant="warning">{lowStock.length}</Badge> : undefined}
          className="h-full"
        >
          {lowStock.length > 0 ? (
            <ul className="space-y-2">
              {lowStock.map((i) => (
                <li key={i.id} className="flex items-center justify-between gap-2 rounded-xl border border-amber-200 bg-amber-50 px-3 py-2">
                  <span className="truncate text-base font-medium text-ink-strong">{i.medicationName}</span>
                  <span className="shrink-0 text-base text-amber-700">{i.quantity} {i.unit}</span>
                </li>
              ))}
            </ul>
          ) : (
            <MiniEmpty icon={<Check className="h-5 w-5" aria-hidden />} text="Stok yeterli." />
          )}
        </BentoCard>
        </motion.div>

        {/* Expiring soon */}
        <motion.div variants={fadeUpItem} className="sm:col-span-2 lg:col-span-2">
        <BentoCard
          title="Yaklaşan SKT"
          icon={<CalendarClock className="h-5 w-5" aria-hidden />}
          accent="bg-orange-50 text-orange-600"
          action={
            <Link to="/expiration" className="flex items-center gap-1 text-base font-medium text-brand-700 hover:underline">
              Tümü <ChevronRight className="h-4 w-4" aria-hidden />
            </Link>
          }
          className="h-full"
        >
          {soon.length > 0 ? (
            <ul className="space-y-2">
              {soon.map((i) => (
                <li key={i.id} className="flex items-center justify-between gap-2 rounded-xl border border-orange-200 bg-orange-50 px-3 py-2">
                  <span className="truncate text-base font-medium text-ink-strong">{i.medicationName}</span>
                  <span className="shrink-0 text-base text-orange-700">{i.expirationDate}</span>
                </li>
              ))}
            </ul>
          ) : (
            <MiniEmpty icon={<Check className="h-5 w-5" aria-hidden />} text="Yakında dolacak ilaç yok." />
          )}
        </BentoCard>
        </motion.div>
      </motion.div>
    </main>
  );
}
