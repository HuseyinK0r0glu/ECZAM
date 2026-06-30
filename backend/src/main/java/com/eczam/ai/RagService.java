package com.eczam.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RawMessageStreamEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
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

    private static final String DECLINE =
            "Bu soruyu ilaç prospektüsünden yanıtlayamıyorum. " +
            "Lütfen eczacınıza veya doktorunuza danışın.";

    private final EmbeddingClient embeddings;
    private final LeafletChunkRepository chunks;
    private final AnthropicClient anthropic;   // null when no API key is configured
    private final String model;
    private final int topK;
    private final double minScore;             // grounding threshold (retrieval gate)

    public RagService(EmbeddingClient embeddings, LeafletChunkRepository chunks,
                      @Value("${ANTHROPIC_API_KEY:}") String apiKey,
                      @Value("${eczam.ai.anthropic-model}") String model,
                      @Value("${eczam.ai.top-k}") int topK,
                      @Value("${eczam.ai.min-score:0.30}") double minScore) {
        this.embeddings = embeddings; this.chunks = chunks;
        // Build the client only when a key is present so the app boots without AI configured.
        this.anthropic = (apiKey == null || apiKey.isBlank())
                ? null
                : AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        this.model = model; this.topK = topK; this.minScore = minScore;
    }

    public record Citation(String section) {}

    /**
     * Streams the grounded answer. onToken receives text deltas; onCitation receives
     * the leaflet sections used; returns true if grounded, false if declined.
     */
    public boolean answer(String question, UUID medicationId, List<String> history,
                          Consumer<String> onToken, Consumer<Citation> onCitation) {
        if (anthropic == null) {
            onToken.accept(DECLINE);
            return false;
        }

        // Retrieval gate: keep only passages above the grounding threshold.
        List<LeafletChunkRepository.Chunk> hits = retrieve(question, medicationId);

        if (hits.isEmpty()) {
            onToken.accept(DECLINE);
            return false;
        }

        // Citations in canonical leaflet order (section ordinal 1..5), deduped.
        citationsOf(hits).forEach(s -> onCitation.accept(new Citation(s)));

        // Caveat the model when any source leaflet was truncated at the dataset's
        // ~32k character ceiling, so the answer flags possible incompleteness.
        String caveat = truncationCaveat(anyTruncated(hits));

        String context = hits.stream()
                .map(c -> "[" + c.sectionName() + "]\n" + c.chunkText())
                .collect(Collectors.joining("\n\n"));

        String userContent = """
                Conversation so far:
                %s

                Leaflet passages:
                %s
                %s
                Question: %s
                """.formatted(String.join("\n", history), context, caveat, question);

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

    // ── Retrieval / gating (pure, model-independent — unit-tested) ────────────

    /** Embeds the question and returns leaflet chunks above the grounding gate. */
    List<LeafletChunkRepository.Chunk> retrieve(String question, UUID medicationId) {
        float[] qvec = embeddings.embed(question);
        return chunks.search(qvec, medicationId, topK).stream()
                .filter(c -> c.score() >= minScore)
                .toList();
    }

    /** Cited section names in canonical leaflet order (ordinal 1..5), deduped. */
    static List<String> citationsOf(List<LeafletChunkRepository.Chunk> hits) {
        return hits.stream()
                .sorted(Comparator.comparing(
                        c -> c.sectionOrdinal() == null ? Integer.MAX_VALUE : c.sectionOrdinal()))
                .map(LeafletChunkRepository.Chunk::sectionName)
                .distinct()
                .toList();
    }

    static boolean anyTruncated(List<LeafletChunkRepository.Chunk> hits) {
        return hits.stream().anyMatch(LeafletChunkRepository.Chunk::truncated);
    }

    static String truncationCaveat(boolean truncated) {
        return truncated
                ? "\nNote: at least one passage comes from a leaflet that was truncated at "
                + "the source, so it may be incomplete — say so and suggest confirming with a "
                + "pharmacist.\n"
                : "";
    }
}
