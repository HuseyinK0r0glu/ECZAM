import { useEffect, useRef, useState } from "react";
import { Html5Qrcode } from "html5-qrcode";

/** Wraps html5-qrcode camera scanning. Renders into the element with id=`regionId`. */
export function useBarcode(regionId: string, onDecode: (code: string) => void, active: boolean) {
  const scannerRef = useRef<Html5Qrcode | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!active) return;
    const scanner = new Html5Qrcode(regionId);
    scannerRef.current = scanner;
    scanner
      .start({ facingMode: "environment" }, { fps: 10, qrbox: 250 },
        (decoded) => { onDecode(decoded); },
        () => {})
      .catch(() => setError("Kameraya erişilemedi. Lütfen elle giriş yapın."));
    return () => { scanner.stop().then(() => scanner.clear()).catch(() => {}); };
  }, [active, regionId, onDecode]);

  return { error };
}
