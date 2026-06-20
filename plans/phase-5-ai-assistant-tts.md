# Phase 5 — AI Assistant & TTS

> **Goal:** the RAG pipeline — leaflet ingestion into pgvector, a streaming (SSE) chat
> endpoint grounded strictly in retrieved leaflet chunks via Anthropic
> `claude-sonnet-4-6`, the chat UI, and text-to-speech playback of leaflet sections.
>
> **Realizes:** EP-07 (TTS), EP-08 · FR-060…064, FR-070…077 · UC-008, UC-009, UC-010.
> **Prerequisites:** [phase-4-notifications.md](phase-4-notifications.md).
> **Exit criteria:** grounded, cited, streamed answers; decline-and-refer when ungrounded;
> any leaflet section can be read aloud.

---

## 1. Dependencies / setup

Add to `backend/pom.xml`:

```xml
<!-- Official Anthropic SDK for the assistant (streaming) -->
<dependency><groupId>com.anthropic</groupId><artifactId>anthropic-java</artifactId><version>2.34.0</version></dependency>
```

Embeddings call OpenAI's REST API via Spring `RestClient` (no extra dependency). The
`leaflet_chunks` table + HNSW index already exist from Phase 1.

Add to `application.yml`:

```yaml
eczam:
  ai:
    anthropic-model: claude-sonnet-4-6      # brief §8.3
    embedding-model: text-embedding-3-small # 1536 dims (matches VECTOR(1536))
    top-k: 5
```

Env vars (already in [00-overview.md](00-overview.md)): `ANTHROPIC_API_KEY`,
`OPENAI_API_KEY`. The Anthropic Java SDK reads `ANTHROPIC_API_KEY` from the environment
automatically.

> **KVKK note (see [docs/security-requirements.md](../docs/security-requirements.md) §6):**
> send only the question + retrieved passages to the LLM — never user identifiers,
> email, or inventory metadata.

Enable async for background ingestion — add `@EnableAsync` to `SchedulingConfig`:

```java
// com.eczam.shared.config.SchedulingConfig
@org.springframework.scheduling.annotation.EnableAsync
// (add alongside @EnableScheduling)
```

---

## 2. Backend

### Embeddings client

#### `backend/src/main/java/com/eczam/ai/EmbeddingClient.java`

```java
package com.eczam.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class EmbeddingClient {

    private final RestClient http;
    private final String model;

    public EmbeddingClient(@Value("${OPENAI_API_KEY:}") String apiKey,
                           @Value("${eczam.ai.embedding-model}") String model) {
        this.model = model;
        this.http = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @SuppressWarnings("unchecked")
    public float[] embed(String text) {
        Map<String, Object> body = http.post().uri("/embeddings")
                .body(Map.of("model", model, "input", text))
                .retrieve().body(Map.class);
        List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
        List<Number> vec = (List<Number>) data.get(0).get("embedding");
        float[] out = new float[vec.size()];
        for (int i = 0; i < vec.size(); i++) out[i] = vec.get(i).floatValue();
        return out;
    }
}
```

### Vector store access (pgvector via JdbcTemplate)

#### `backend/src/main/java/com/eczam/ai/LeafletChunkRepository.java`

```java
package com.eczam.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class LeafletChunkRepository {

    public record Chunk(String sectionName, String chunkText, double score) {}

    private final JdbcTemplate jdbc;
    public LeafletChunkRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(UUID medicationId, String sectionName, String chunkText, float[] embedding, int chunkIndex) {
        jdbc.update("""
                INSERT INTO leaflet_chunks (medication_id, section_name, chunk_text, embedding, chunk_index)
                VALUES (?, ?, ?, ?::vector, ?)
                """, medicationId, sectionName, chunkText, toVectorLiteral(embedding), chunkIndex);
    }

    /** Top-k by cosine similarity; optional medication filter. (<=> is pgvector cosine distance.) */
    public List<Chunk> search(float[] queryEmbedding, UUID medicationId, int k) {
        String vec = toVectorLiteral(queryEmbedding);
        return jdbc.query("""
                SELECT section_name, chunk_text, 1 - (embedding <=> ?::vector) AS score
                FROM leaflet_chunks
                WHERE (?::uuid IS NULL OR medication_id = ?::uuid)
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """,
                (rs, n) -> new Chunk(rs.getString("section_name"), rs.getString("chunk_text"), rs.getDouble("score")),
                vec,
                medicationId == null ? null : medicationId.toString(),
                medicationId == null ? null : medicationId.toString(),
                vec, k);
    }

    private static String toVectorLiteral(float[] v) {
        return "[" + java.util.stream.IntStream.range(0, v.length)
                .mapToObj(i -> Float.toString(v[i])).collect(Collectors.joining(",")) + "]";
    }
}
```

### Ingestion pipeline (UC-010)

#### `backend/src/main/java/com/eczam/ai/LeafletIndexer.java`

```java
package com.eczam.ai;

import com.eczam.medications.LeafletSections;
import com.eczam.medications.Medication;
import com.eczam.medications.MedicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Section split → ~300-token chunks → embed → store (brief §8.2). Triggered on catalog insert. */
@Service
public class LeafletIndexer {

    private static final Logger log = LoggerFactory.getLogger(LeafletIndexer.class);
    private static final int CHUNK_CHARS = 1200;   // ~300 tokens
    private static final int OVERLAP = 200;

    private final EmbeddingClient embeddings;
    private final LeafletChunkRepository chunks;
    private final MedicationRepository medications;

    public LeafletIndexer(EmbeddingClient embeddings, LeafletChunkRepository chunks, MedicationRepository medications) {
        this.embeddings = embeddings; this.chunks = chunks; this.medications = medications;
    }

    @Async
    @Transactional
    public void ingest(UUID medicationId) {
        Medication med = medications.findById(medicationId).orElse(null);
        if (med == null || med.getLeafletSections() == null) return;
        try {
            int index = 0;
            for (Map.Entry<String, String> e : sectionsOf(med.getLeafletSections()).entrySet()) {
                if (e.getValue() == null || e.getValue().isBlank()) continue;
                for (String chunk : chunk(e.getValue())) {
                    chunks.insert(medicationId, e.getKey(), chunk, embeddings.embed(chunk), index++);
                }
            }
            med.setVectorIndexed(true);
        } catch (Exception ex) {
            log.error("Leaflet ingestion failed for {}: {}", medicationId, ex.getMessage());
            // leave vector_indexed = false → assistant declines gracefully for this med
        }
    }

    private static Map<String, String> sectionsOf(LeafletSections s) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("dosage", s.dosage());
        m.put("side_effects", s.sideEffects());
        m.put("contraindications", s.contraindications());
        m.put("storage", s.storage());
        m.put("interactions", s.interactions());
        m.put("missed_dose", s.missedDose());
        return m;
    }

    private static java.util.List<String> chunk(String text) {
        java.util.List<String> out = new java.util.ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int end = Math.min(text.length(), i + CHUNK_CHARS);
            out.add(text.substring(i, end));
            if (end == text.length()) break;
            i = end - OVERLAP;
        }
        return out;
    }
}
```

> **Wire the trigger** in `MedicationService` (Phase 2): inject `LeafletIndexer` and call
> `indexer.ingest(m.getId())` after `repo.save(m)` in both `create(...)` and the OpenFDA
> branch of `lookupByBarcode(...)` (replacing the "Phase 5: …" comments).

### RAG query service (streaming) — UC-009

#### `backend/src/main/java/com/eczam/ai/RagService.java`

```java
package com.eczam.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RawMessageStreamEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class RagService {

    /** Verbatim system prompt from brief §8.4. */
    private static final String SYSTEM_PROMPT = """
        You are ECZAM Assistant, a medication information helper embedded in the ECZAM platform.
        You answer questions strictly based on the medication leaflet passages provided to you in the context.
        Do not speculate beyond what the passages say. Do not provide general medical advice.
        If a question cannot be answered from the provided passages, say so clearly and suggest the user consult their pharmacist or physician.
        Always cite which section of the leaflet your answer comes from.
        Respond in the same language the user writes in.
        """;

    private static final double MIN_SCORE = 0.30; // grounding threshold

    private final EmbeddingClient embeddings;
    private final LeafletChunkRepository chunks;
    private final AnthropicClient anthropic;
    private final String model;
    private final int topK;

    public RagService(EmbeddingClient embeddings, LeafletChunkRepository chunks,
                      @Value("${eczam.ai.anthropic-model}") String model,
                      @Value("${eczam.ai.top-k}") int topK) {
        this.embeddings = embeddings; this.chunks = chunks;
        this.anthropic = AnthropicOkHttpClient.fromEnv(); // reads ANTHROPIC_API_KEY
        this.model = model; this.topK = topK;
    }

    public record Citation(String section) {}

    /**
     * Streams the grounded answer. onToken receives text deltas; onCitation receives
     * the leaflet sections used; returns true if grounded, false if declined.
     */
    public boolean answer(String question, UUID medicationId, List<String> history,
                          Consumer<String> onToken, Consumer<Citation> onCitation) {
        float[] qvec = embeddings.embed(question);
        List<LeafletChunkRepository.Chunk> hits = chunks.search(qvec, medicationId, topK).stream()
                .filter(c -> c.score() >= MIN_SCORE).toList();

        if (hits.isEmpty()) {
            onToken.accept("Bu soruyu ilaç prospektüsünden yanıtlayamıyorum. " +
                           "Lütfen eczacınıza veya doktorunuza danışın.");
            return false;
        }

        hits.stream().map(LeafletChunkRepository.Chunk::sectionName).distinct()
                .forEach(s -> onCitation.accept(new Citation(s)));

        String context = hits.stream()
                .map(c -> "[" + c.sectionName() + "]\n" + c.chunkText())
                .collect(Collectors.joining("\n\n"));

        String userContent = """
                Conversation so far:
                %s

                Leaflet passages:
                %s

                Question: %s
                """.formatted(String.join("\n", history), context, question);

        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)                 // "claude-sonnet-4-6"
                .maxTokens(1024L)
                .system(SYSTEM_PROMPT)
                .addUserMessage(userContent)
                .build();

        try (StreamResponse<RawMessageStreamEvent> stream = anthropic.messages().createStreaming(params)) {
            stream.stream()
                    .flatMap(event -> event.contentBlockDelta().stream())
                    .flatMap(delta -> delta.delta().text().stream())
                    .forEach(textDelta -> onToken.accept(textDelta.text()));
        }
        return true;
    }
}
```

### Streaming SSE endpoint

#### `backend/src/main/java/com/eczam/ai/dto/ChatDtos.java`

```java
package com.eczam.ai.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class ChatDtos {
    public record ChatRequest(@NotBlank String message, String medicationId, List<String> history) {}
    private ChatDtos() {}
}
```

#### `backend/src/main/java/com/eczam/ai/ChatController.java`

```java
package com.eczam.ai;

import com.eczam.ai.dto.ChatDtos.ChatRequest;
import com.eczam.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/ai")
public class ChatController {

    private final RagService rag;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatController(RagService rag) { this.rag = rag; }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@CurrentUser UUID userId, @Valid @RequestBody ChatRequest req) {
        SseEmitter emitter = new SseEmitter(60_000L);
        UUID medId = req.medicationId() == null ? null : UUID.fromString(req.medicationId());
        List<String> history = req.history() == null ? List.of() : req.history();

        executor.submit(() -> {
            try {
                boolean grounded = rag.answer(req.message(), medId, history,
                        token -> sendQuietly(emitter, "token", token),
                        cite -> sendQuietly(emitter, "citation", cite.section()));
                emitter.send(SseEmitter.event().name("done").data("{\"grounded\":" + grounded + "}"));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private void sendQuietly(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException ignored) { /* client disconnected */ }
    }
}
```

> Anthropic call details (confirmed against the Claude API reference): endpoint
> `POST /v1/messages`, header `anthropic-version: 2023-06-01`, `stream: true`, deltas on
> `content_block_delta`. The Java SDK encapsulates these — model `claude-sonnet-4-6`
> (the brief's choice), adaptive thinking not used for this short, grounded task.

---

## 3. Frontend

### `frontend/src/services/aiService.ts` (SSE over POST)

```ts
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
        for (const line of frame.split("\n")) {
          if (line.startsWith("event:")) event = line.slice(6).trim();
          else if (line.startsWith("data:")) data += line.slice(5).trim();
        }
        if (event === "token") cb.onToken(data);
        else if (event === "citation") cb.onCitation(data);
        else if (event === "done") cb.onDone(JSON.parse(data || "{}").grounded ?? false);
      }
    }
  } catch (err) {
    cb.onError(err);
  }
}
```

### `frontend/src/pages/AiAssistant.tsx`

```tsx
import { useState, useRef } from "react";
import { streamChat } from "../services/aiService";

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
    <main className="mx-auto flex h-[80vh] max-w-2xl flex-col p-6">
      <h1 className="mb-4 text-3xl font-bold">ECZAM Asistan</h1>
      <div className="flex-1 space-y-4 overflow-y-auto">
        {messages.map((m, i) => (
          <div key={i} className={m.role === "user" ? "text-right" : "text-left"}>
            <div className={`inline-block max-w-[85%] rounded-lg p-3 text-lg ${
              m.role === "user" ? "bg-blue-700 text-white" : "bg-gray-100"}`}>
              <p className="whitespace-pre-wrap">{m.text || "…"}</p>
              {m.citations && m.citations.length > 0 && (
                <p className="mt-2 text-sm text-gray-600">Kaynak: {m.citations.join(", ")}</p>
              )}
            </div>
          </div>
        ))}
      </div>
      <div className="mt-4 flex gap-2">
        <input value={input} onChange={(e) => setInput(e.target.value)}
               onKeyDown={(e) => e.key === "Enter" && send()}
               placeholder="İlaçlarınız hakkında soru sorun…"
               className="flex-1 rounded border p-3 text-lg" />
        <button onClick={send} disabled={streaming}
                className="rounded bg-blue-700 px-5 text-lg text-white disabled:opacity-50">Gönder</button>
      </div>
    </main>
  );
}
```

### `frontend/src/hooks/useTTS.ts` (Web Speech API — brief §10)

```ts
import { useCallback, useEffect, useRef, useState } from "react";

export function useTTS() {
  const [speaking, setSpeaking] = useState(false);
  const [paused, setPaused] = useState(false);
  const voiceRef = useRef<SpeechSynthesisVoice | null>(null);

  useEffect(() => {
    function pickVoice() {
      const voices = window.speechSynthesis.getVoices();
      const lang = navigator.language || "tr-TR";
      voiceRef.current = voices.find((v) => v.lang.startsWith(lang.split("-")[0])) ?? voices[0] ?? null;
    }
    pickVoice();
    window.speechSynthesis.onvoiceschanged = pickVoice;
    return () => window.speechSynthesis.cancel();
  }, []);

  const play = useCallback((text: string) => {
    window.speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(text);
    if (voiceRef.current) { u.voice = voiceRef.current; u.lang = voiceRef.current.lang; }
    u.onend = () => { setSpeaking(false); setPaused(false); };
    u.onerror = () => { setSpeaking(false); setPaused(false); };
    window.speechSynthesis.speak(u);
    setSpeaking(true); setPaused(false);
  }, []);

  const pause = useCallback(() => { window.speechSynthesis.pause(); setPaused(true); }, []);
  const resume = useCallback(() => { window.speechSynthesis.resume(); setPaused(false); }, []);
  const stop = useCallback(() => { window.speechSynthesis.cancel(); setSpeaking(false); setPaused(false); }, []);

  return { speaking, paused, play, pause, resume, stop };
}
```

### `frontend/src/features/medications/TtsControlBar.tsx`

```tsx
import { useState } from "react";
import { useTTS } from "../../hooks/useTTS";
import type { LeafletSections } from "../../services/medicationService";

const LABELS: Record<string, string> = {
  dosage: "Doz", side_effects: "Yan etkiler", contraindications: "Kullanılmaması gereken durumlar",
  storage: "Saklama", interactions: "Etkileşimler", missed_dose: "Doz atlanırsa",
};

export default function TtsControlBar({ sections }: { sections: LeafletSections }) {
  const { speaking, paused, play, pause, resume, stop } = useTTS();
  const available = Object.entries(LABELS).filter(([k]) => (sections as Record<string, string | undefined>)[k]);
  const [section, setSection] = useState(available[0]?.[0] ?? "dosage");

  return (
    <div role="region" aria-label="Sesli okuma" className="sticky bottom-0 mt-4 flex flex-wrap items-center gap-2 rounded border bg-white p-3">
      <label className="text-lg">Bölüm:
        <select value={section} onChange={(e) => setSection(e.target.value)}
                className="ml-2 rounded border p-2 text-lg">
          {available.map(([k]) => <option key={k} value={k}>{LABELS[k]}</option>)}
        </select>
      </label>
      {!speaking && (
        <button onClick={() => play((sections as Record<string, string>)[section] ?? "")}
                className="rounded bg-blue-700 px-4 py-2 text-lg text-white">▶ Oynat</button>
      )}
      {speaking && !paused && <button onClick={pause} className="rounded border px-4 py-2 text-lg">⏸ Duraklat</button>}
      {speaking && paused && <button onClick={resume} className="rounded border px-4 py-2 text-lg">▶ Devam</button>}
      {speaking && <button onClick={stop} className="rounded border px-4 py-2 text-lg">⏹ Durdur</button>}
    </div>
  );
}
```

> Add `<TtsControlBar sections={med.leafletSections} />` to `MedicationDetail.tsx`
> (Phase 2) when `med.leafletSections` is present. All controls are keyboard-operable
> (FR-064).

### Wire route — `App.tsx`

```tsx
import AiAssistant from "./pages/AiAssistant";
// inside ProtectedRoute:
<Route path="/assistant" element={<AiAssistant />} />
```

---

## 4. Exit criteria (Phase 5)

- [ ] Adding a catalog medication triggers background ingestion; `vector_indexed` flips true.
- [ ] Asking an answerable question streams a grounded answer with a cited section.
- [ ] Asking an unanswerable question declines and refers to a pharmacist/physician.
- [ ] Scoping to a medication restricts retrieval to that medicine.
- [ ] Any leaflet section can be played, paused, resumed, and stopped via TTS.

## 5. Tests (Phase 5)

- RAG eval ([docs/test-plan.md](../docs/test-plan.md) §7.1): grounded answers cite the
  correct section; out-of-leaflet questions are declined (assert no fabrication);
  medication scoping filters retrieval; prompt-injection in leaflet/user text cannot
  override guardrails.
- Vector search: insert chunks → cosine search returns nearest first.
- Frontend: `aiService` SSE frame parsing; `useTTS` lifecycle (play/pause/stop, voice
  fallback).

Covers FR-060…064, FR-070…077, NFR-082, UC-008/009/010. Next:
[phase-6-pwa-polish.md](phase-6-pwa-polish.md).
