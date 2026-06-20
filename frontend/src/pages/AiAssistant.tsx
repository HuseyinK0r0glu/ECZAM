import { useState, useRef } from "react";
import { Send, Sparkles } from "lucide-react";
import { streamChat } from "../services/aiService";
import { Button, PageHeader } from "../components/ui";

interface Msg { role: "user" | "assistant"; text: string; citations?: string[]; }

export default function AiAssistant() {
  const [messages, setMessages] = useState<Msg[]>([]);
  const [input, setInput] = useState("");
  const [streaming, setStreaming] = useState(false);
  const citationsRef = useRef<string[]>([]);

  async function send() {
    const question = input.trim();
    if (!question || streaming) return;
    setInput("");
    setMessages((m) => [...m, { role: "user", text: question }, { role: "assistant", text: "" }]);
    setStreaming(true);
    citationsRef.current = [];

    const history = messages.map((m) => `${m.role}: ${m.text}`);
    await streamChat(question, { history }, {
      onToken: (t) => setMessages((m) => {
        const copy = [...m]; copy[copy.length - 1] = { ...copy[copy.length - 1], text: copy[copy.length - 1].text + t };
        return copy;
      }),
      onCitation: (s) => { citationsRef.current = [...new Set([...citationsRef.current, s])]; },
      onDone: () => {
        setMessages((m) => {
          const copy = [...m];
          copy[copy.length - 1] = { ...copy[copy.length - 1], citations: citationsRef.current };
          return copy;
        });
        setStreaming(false);
      },
      onError: () => {
        setMessages((m) => {
          const copy = [...m];
          copy[copy.length - 1] = { ...copy[copy.length - 1], text: "Bir hata oluştu. Tekrar deneyin." };
          return copy;
        });
        setStreaming(false);
      },
    });
  }

  return (
    <main className="mx-auto max-w-3xl px-4 py-6 sm:px-6">
      <PageHeader title="ECZAM Asistan" subtitle="Yanıtlar yalnızca ilaç prospektüslerine dayanır" />

      {messages.length === 0 ? (
        <div className="flex flex-col items-center px-4 py-10 text-center">
          <div
            className="flex h-16 w-16 items-center justify-center rounded-full bg-brand-100 text-brand-700 ring-8 ring-brand-50"
            aria-hidden
          >
            <Sparkles className="h-8 w-8" />
          </div>
          <p className="mt-5 text-xl font-semibold tracking-tight text-ink-strong">İlaçlarınız hakkında soru sorun</p>
          <p className="mt-1.5 max-w-md text-base text-ink-muted">
            Örneğin "Bu ilacı aç karnına alabilir miyim?" Yanıtlar prospektüs bilgilerinden
            alınır ve kaynağı belirtilir.
          </p>
        </div>
      ) : (
        <div className="space-y-4">
          {messages.map((m, i) => (
            <div key={i} className={m.role === "user" ? "flex justify-end" : "flex justify-start"}>
              <div
                className={`max-w-[85%] rounded-2xl px-4 py-3 text-lg ${
                  m.role === "user"
                    ? "bg-brand-700 text-white"
                    : "bg-surface text-ink-strong shadow-sm ring-1 ring-zinc-200/50"
                }`}
              >
                <p className="whitespace-pre-wrap">{m.text || "…"}</p>
                {m.citations && m.citations.length > 0 && (
                  <p className={`mt-2 text-sm ${m.role === "user" ? "text-white/80" : "text-ink-muted"}`}>
                    Kaynak: {m.citations.join(", ")}
                  </p>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="sticky bottom-24 z-10 mt-6 md:bottom-4">
        <div className="flex gap-2 rounded-2xl bg-surface/95 p-2 shadow-md ring-1 ring-zinc-200/50 backdrop-blur">
          <input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && send()}
            placeholder="İlaçlarınız hakkında soru sorun…"
            className="flex-1 rounded-xl border-0 bg-transparent px-3 py-2.5 text-lg text-ink-strong placeholder:text-ink-muted focus:outline-none"
          />
          <Button onClick={send} disabled={streaming} className="shrink-0" aria-label="Gönder">
            <Send className="h-5 w-5" aria-hidden /> Gönder
          </Button>
        </div>
      </div>
    </main>
  );
}
