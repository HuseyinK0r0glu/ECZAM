import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { PenLine, ScanLine } from "lucide-react";
import BarcodeScanner from "./BarcodeScanner";
import { createMedication, getByBarcode, type MedicationDetail } from "../../services/medicationService";
import { addInventory } from "../../services/inventoryService";
import { Alert, Button, Card, Field, Input, PageHeader } from "../../components/ui";

export default function AddMedicationForm() {
  const nav = useNavigate();
  const [mode, setMode] = useState<"choose" | "scan" | "form">("choose");
  const [med, setMed] = useState<Partial<MedicationDetail>>({});
  const [quantity, setQuantity] = useState(30);
  const [unit, setUnit] = useState("pill");
  const [expirationDate, setExpirationDate] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function onScanned(code: string) {
    try {
      const found = await getByBarcode(code);
      setMed(found); setMode("form");
    } catch {
      setMed({ barcode: code }); setMode("form");
      setError("Barkod bulunamadı. Lütfen bilgileri elle tamamlayın.");
    }
  }

  async function onSave() {
    setError(null);
    try {
      let medicationId = med.id;
      if (!medicationId) {
        const created = await createMedication({ name: med.name!, barcode: med.barcode,
          manufacturer: med.manufacturer, form: med.form, strength: med.strength });
        medicationId = created.id;
      }
      await addInventory({ medicationId: medicationId!, quantity, unit,
        expirationDate: expirationDate || undefined });
      nav("/inventory");
    } catch (e: any) {
      const code = e?.response?.data?.error?.code;
      setError(code === "INVENTORY_BATCH_EXISTS" ? "Bu ilaç ve son kullanma tarihi zaten envanterde." : "Kaydedilemedi.");
    }
  }

  const alert = error && <Alert variant="warning">{error}</Alert>;

  if (mode === "choose") return (
    <main className="mx-auto max-w-xl px-4 py-6 sm:px-6">
      <PageHeader title="İlaç Ekle" subtitle="Nasıl eklemek istersiniz?" />
      <div className="grid gap-4 sm:grid-cols-2">
        <button
          onClick={() => setMode("scan")}
          className="card card-interactive flex flex-col items-center gap-3 p-8 text-center"
        >
          <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-brand-700 text-white">
            <ScanLine className="h-7 w-7" aria-hidden />
          </span>
          <span className="text-lg font-semibold text-ink-strong">Barkod Tara</span>
        </button>
        <button
          onClick={() => setMode("form")}
          className="card card-interactive flex flex-col items-center gap-3 p-8 text-center"
        >
          <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-brand-50 text-brand-700">
            <PenLine className="h-7 w-7" aria-hidden />
          </span>
          <span className="text-lg font-semibold text-ink-strong">Elle Ekle</span>
        </button>
      </div>
    </main>
  );

  if (mode === "scan") return (
    <main className="mx-auto max-w-xl px-4 py-6 sm:px-6">
      <PageHeader title="Barkod Tara" />
      <BarcodeScanner onDecode={onScanned} onCancel={() => setMode("form")} />
    </main>
  );

  return (
    <main className="mx-auto max-w-xl px-4 py-6 sm:px-6">
      <PageHeader title="İlaç Bilgileri" />
      <Card className="space-y-5">
        {alert}
        <Field label="İlaç adı">
          <Input required value={med.name ?? ""} onChange={(e) => setMed({ ...med, name: e.target.value })} />
        </Field>
        <Field label="Üretici">
          <Input value={med.manufacturer ?? ""} onChange={(e) => setMed({ ...med, manufacturer: e.target.value })} />
        </Field>
        <div className="flex gap-4">
          <div className="flex-1">
            <Field label="Adet">
              <Input type="number" min={0} value={quantity} onChange={(e) => setQuantity(Number(e.target.value))} />
            </Field>
          </div>
          <div className="flex-1">
            <Field label="Birim">
              <Input value={unit} onChange={(e) => setUnit(e.target.value)} />
            </Field>
          </div>
        </div>
        <Field label="Son kullanma tarihi">
          <Input type="date" value={expirationDate} onChange={(e) => setExpirationDate(e.target.value)} />
        </Field>
        <Button onClick={onSave} disabled={!med.name} block>
          Kaydet
        </Button>
      </Card>
    </main>
  );
}
