import { useState } from "react";
import { Pause, Play, Square, Volume2 } from "lucide-react";
import { useTTS } from "../../hooks/useTTS";
import type { LeafletSections } from "../../services/medicationService";
import { Button } from "../../components/ui";

const LABELS: Record<string, string> = {
  dosage: "Doz", side_effects: "Yan etkiler", contraindications: "Kullanılmaması gereken durumlar",
  storage: "Saklama", interactions: "Etkileşimler", missed_dose: "Doz atlanırsa",
};

export default function TtsControlBar({ sections }: { sections: LeafletSections }) {
  const { speaking, paused, play, pause, resume, stop } = useTTS();
  const available = Object.entries(LABELS).filter(([k]) => (sections as Record<string, string | undefined>)[k]);
  const [section, setSection] = useState(available[0]?.[0] ?? "dosage");

  return (
    <div
      role="region"
      aria-label="Sesli okuma"
      className="sticky bottom-24 z-10 mt-6 md:bottom-4"
    >
      <div className="mx-auto flex max-w-2xl flex-wrap items-center gap-2 rounded-2xl bg-surface/95 p-3 shadow-md ring-1 ring-zinc-200/50 backdrop-blur">
        <span className="flex items-center gap-2 text-base font-medium text-ink-strong">
          <Volume2 className="h-5 w-5 text-brand-700" aria-hidden />
          <label className="flex items-center gap-2">
            Bölüm:
            <select
              value={section}
              onChange={(e) => setSection(e.target.value)}
              className="rounded-lg border border-line bg-surface px-2 py-1.5 text-base"
            >
              {available.map(([k]) => <option key={k} value={k}>{LABELS[k]}</option>)}
            </select>
          </label>
        </span>
        <span className="ml-auto flex items-center gap-2">
          {!speaking && (
            <Button onClick={() => play((sections as Record<string, string>)[section] ?? "")} className="min-h-0 px-4 py-2 text-base">
              <Play className="h-5 w-5" aria-hidden /> Oynat
            </Button>
          )}
          {speaking && !paused && (
            <Button variant="secondary" onClick={pause} className="min-h-0 px-4 py-2 text-base">
              <Pause className="h-5 w-5" aria-hidden /> Duraklat
            </Button>
          )}
          {speaking && paused && (
            <Button variant="secondary" onClick={resume} className="min-h-0 px-4 py-2 text-base">
              <Play className="h-5 w-5" aria-hidden /> Devam
            </Button>
          )}
          {speaking && (
            <Button variant="secondary" onClick={stop} className="min-h-0 px-4 py-2 text-base">
              <Square className="h-5 w-5" aria-hidden /> Durdur
            </Button>
          )}
        </span>
      </div>
    </div>
  );
}
