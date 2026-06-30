import { WifiOff } from "lucide-react";

export default function Offline() {
  return (
    <main className="grid min-h-screen place-items-center bg-canvas p-6">
      <div className="w-full max-w-md text-center">
        <span className="mx-auto mb-6 flex h-20 w-20 items-center justify-center rounded-full bg-brand-50 text-brand-700">
          <WifiOff className="h-10 w-10" aria-hidden />
        </span>
        <h1 className="text-3xl font-semibold tracking-tight text-ink-strong">Çevrimdışısınız</h1>
        <p className="mt-4 text-lg text-ink">
          İnternet bağlantısı yok. Daha önce görüntülenen bilgiler kullanılabilir;
          yeni veriler bağlantı geri geldiğinde güncellenecek.
        </p>
      </div>
    </main>
  );
}
