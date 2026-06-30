import { tokenStore } from "./apiClient";

const BASE = import.meta.env.VITE_API_URL ?? "http://localhost:8080/api/v1";

export interface ChatCallbacks {
  onToken: (text: string) => void;
  onCitation: (section: string) => void;
  onDone: (grounded: boolean) => void;
  onError: (err: unknown) => void;
}

/** POST /ai/chat returns text/event-stream; parse SSE manually (EventSource is GET-only). */
export async function streamChat(
  message: string,
  opts: { medicationId?: string; history?: string[] },
  cb: ChatCallbacks,
) {
  try {
    const res = await fetch(`${BASE}/ai/chat`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "text/event-stream",
        Authorization: `Bearer ${tokenStore.access()}`,
      },
      body: JSON.stringify({ message, medicationId: opts.medicationId, history: opts.history ?? [] }),
    });
    if (!res.body) throw new Error("No stream");

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";

    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });

      const frames = buffer.split("\n\n");
      buffer = frames.pop() ?? "";
      for (const frame of frames) {
        let event = "message";
        let data = "";
        for (const raw of frame.split("\n")) {
          const line = raw.replace(/\r$/, "");
          if (line.startsWith("event:")) event = line.slice(6).trim();
          // Keep data verbatim: trimming would drop the leading spaces between
          // streamed tokens and run words together (e.g. "Merhaba" + " dünya").
          else if (line.startsWith("data:")) data += line.slice(5);
        }
        if (event === "token") cb.onToken(data);
        else if (event === "citation") cb.onCitation(data.trim());
        else if (event === "done") cb.onDone(JSON.parse(data || "{}").grounded ?? false);
      }
    }
  } catch (err) {
    cb.onError(err);
  }
}
