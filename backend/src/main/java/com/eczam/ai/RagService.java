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

    private static final String DECLINE =
            "Bu soruyu ilaç prospektüsünden yanıtlayamıyorum. " +
            "Lütfen eczacınıza veya doktorunuza danışın.";

    private final EmbeddingClient embeddings;
    private final LeafletChunkRepository chunks;
    private final AnthropicClient anthropic;   // null when no API key is configured
    private final String model;
    private final int topK;

    public RagService(EmbeddingClient embeddings, LeafletChunkRepository chunks,
                      @Value("${ANTHROPIC_API_KEY:}") String apiKey,
                      @Value("${eczam.ai.anthropic-model}") String model,
                      @Value("${eczam.ai.top-k}") int topK) {
        this.embeddings = embeddings; this.chunks = chunks;
        // Build the client only when a key is present so the app boots without AI configured.
        this.anthropic = (apiKey == null || apiKey.isBlank())
                ? null
                : AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        this.model = model; this.topK = topK;
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

        float[] qvec = embeddings.embed(question);
        List<LeafletChunkRepository.Chunk> hits = chunks.search(qvec, medicationId, topK).stream()
                .filter(c -> c.score() >= MIN_SCORE).toList();

        if (hits.isEmpty()) {
            onToken.accept(DECLINE);
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
