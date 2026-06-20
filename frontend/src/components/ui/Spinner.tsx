export default function Spinner({ label = "Yükleniyor…" }: { label?: string }) {
  return (
    <div role="status" className="flex items-center justify-center gap-3 p-10 text-lg text-ink-muted">
      <span
        className="h-6 w-6 animate-spin rounded-full border-[3px] border-line border-t-brand-700"
        aria-hidden
      />
      <span>{label}</span>
    </div>
  );
}
