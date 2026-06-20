import { describe, it, expect, vi, afterEach } from "vitest";
import { streamChat } from "./aiService";

function sseStream(frames: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  return new ReadableStream({
    start(controller) {
      // Push frames split oddly across chunks to exercise the buffer logic.
      const blob = frames.join("");
      const mid = Math.floor(blob.length / 2);
      controller.enqueue(encoder.encode(blob.slice(0, mid)));
      controller.enqueue(encoder.encode(blob.slice(mid)));
      controller.close();
    },
  });
}

afterEach(() => { vi.unstubAllGlobals(); });

describe("streamChat SSE parsing", () => {
  it("dispatches token, citation and done events in order", async () => {
    // Spring SseEmitter wire format: `event:<name>` / `data:<value>` (no added space).
    // The space before "dünya" is part of the streamed token and must be preserved.
    const frames = [
      "event:token\ndata:Merhaba\n\n",
      "event:token\ndata: dünya\n\n",
      "event:citation\ndata:dosage\n\n",
      'event:done\ndata:{"grounded":true}\n\n',
    ];
    vi.stubGlobal("fetch", vi.fn(async () => new Response(sseStream(frames), {
      headers: { "Content-Type": "text/event-stream" },
    })));

    const tokens: string[] = [];
    const citations: string[] = [];
    let grounded: boolean | undefined;

    await streamChat("soru", {}, {
      onToken: (t) => tokens.push(t),
      onCitation: (s) => citations.push(s),
      onDone: (g) => { grounded = g; },
      onError: () => { throw new Error("should not error"); },
    });

    expect(tokens.join("")).toBe("Merhaba dünya");
    expect(citations).toEqual(["dosage"]);
    expect(grounded).toBe(true);
  });

  it("calls onError when there is no response body", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => ({ body: null } as Response)));
    const onError = vi.fn();
    await streamChat("soru", {}, {
      onToken: () => {}, onCitation: () => {}, onDone: () => {}, onError,
    });
    expect(onError).toHaveBeenCalledOnce();
  });
});
