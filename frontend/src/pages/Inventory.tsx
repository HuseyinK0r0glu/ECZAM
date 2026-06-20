import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { motion } from "motion/react";
import { ChevronRight, PackageOpen, Plus } from "lucide-react";
import { listInventory, type InventoryItem } from "../services/inventoryService";
import { Badge, buttonVariants, EmptyState, PageHeader, Spinner } from "../components/ui";
import { fadeUpContainer, fadeUpItem } from "../utils/motion";

function ExpiryBadge({ status }: { status: InventoryItem["expiryStatus"] }) {
  if (status === "EXPIRED") return <Badge variant="danger">Süresi doldu</Badge>;
  if (status === "EXPIRING_SOON") return <Badge variant="orange">Yakında dolacak</Badge>;
  return null;
}

export default function Inventory() {
  const { data, isLoading } = useQuery({ queryKey: ["inventory"], queryFn: listInventory });
  if (isLoading) return <Spinner />;

  return (
    <main className="mx-auto max-w-3xl px-4 py-6 sm:px-6">
      <PageHeader
        title="Envanter"
        subtitle="İlaçlarınız ve stok durumu"
        action={
          <Link to="/inventory/add" className={buttonVariants()}>
            <Plus className="h-5 w-5" aria-hidden /> Ekle
          </Link>
        }
      />

      {data?.length === 0 ? (
        <EmptyState
          icon={<PackageOpen className="h-8 w-8" aria-hidden />}
          title="Henüz ilaç eklemediniz"
          description="İlk ilacınızı ekleyerek stok, doz ve son kullanma takibine başlayın."
          action={
            <Link to="/inventory/add" className={buttonVariants()}>
              <Plus className="h-5 w-5" aria-hidden /> İlaç Ekle
            </Link>
          }
        />
      ) : (
        <motion.ul
          variants={fadeUpContainer}
          initial="hidden"
          animate="show"
          className="space-y-3"
        >
          {data?.map((item) => (
            <motion.li key={item.id} variants={fadeUpItem}>
              <Link
                to={`/medications/${item.medicationId}?um=${item.id}`}
                className="card card-interactive flex items-center justify-between gap-4 p-4"
              >
                <div className="min-w-0">
                  <p className="truncate text-xl font-semibold text-ink-strong">{item.medicationName}</p>
                  <div className="mt-2 flex flex-wrap gap-2">
                    {item.lowStock && <Badge variant="warning">Az kaldı</Badge>}
                    <ExpiryBadge status={item.expiryStatus} />
                  </div>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  <span className="text-lg font-medium text-ink">{item.quantity} {item.unit}</span>
                  <ChevronRight className="h-5 w-5 text-ink-muted" aria-hidden />
                </div>
              </Link>
            </motion.li>
          ))}
        </motion.ul>
      )}
    </main>
  );
}
