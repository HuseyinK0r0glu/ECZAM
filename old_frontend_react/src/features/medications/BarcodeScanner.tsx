import { useBarcode } from "../../hooks/useBarcode";
import { Alert, Button } from "../../components/ui";

export default function BarcodeScanner({ onDecode, onCancel }: {
  onDecode: (code: string) => void; onCancel: () => void;
}) {
  const { error } = useBarcode("scanner-region", onDecode, true);
  return (
    <div role="dialog" aria-label="Barkod tarayıcı" className="card space-y-4 p-5">
      <p className="text-lg text-ink">Barkodu kameraya gösterin.</p>
      <div id="scanner-region" className="mx-auto w-full max-w-sm overflow-hidden rounded-xl border border-line" />
      {error && <Alert variant="error">{error}</Alert>}
      <Button variant="secondary" onClick={onCancel} block>
        Elle giriş yap
      </Button>
    </div>
  );
}
