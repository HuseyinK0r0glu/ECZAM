package com.eczam.ai;

import com.eczam.ai.LeafletChunkRepository.Chunk;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the model-independent retrieval/gating logic — the hallucination
 * guardrail (CLAUDE.md §7). The Anthropic call itself isn't exercised here.
 */
class RagServiceTest {

    private final EmbeddingClient embeddings = mock(EmbeddingClient.class);
    private final LeafletChunkRepository chunks = mock(LeafletChunkRepository.class);

    private RagService service(String apiKey) {
        return new RagService(embeddings, chunks, apiKey, "claude-sonnet-4-6", 5, 0.30);
    }

    @Test
    void without_an_api_key_it_declines_before_retrieving() {
        List<String> tokens = new ArrayList<>();
        boolean grounded = service("").answer(
                "yan etkileri neler?", UUID.randomUUID(), List.of(), tokens::add, c -> {});

        assertThat(grounded).isFalse();
        assertThat(String.join("", tokens)).contains("eczacınıza"); // pharmacist guardrail
        verifyNoInteractions(embeddings, chunks);
    }

    @Test
    void retrieve_drops_passages_below_the_grounding_threshold() {
        when(embeddings.embed(anyString())).thenReturn(new float[]{1f});
        when(chunks.search(any(), any(), anyInt())).thenReturn(List.of(
                new Chunk("side_effects", 4, "relevant", 0.92, false),
                new Chunk("storage", 5, "noise", 0.10, false)));

        List<Chunk> hits = service("").retrieve("q", null);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).sectionName()).isEqualTo("side_effects");
    }

    @Test
    void citations_are_ordinal_ordered_and_deduped() {
        List<Chunk> hits = List.of(
                new Chunk("side_effects", 4, "a", 0.9, false),
                new Chunk("what_is", 1, "b", 0.8, false),
                new Chunk("side_effects", 4, "c", 0.7, false));

        assertThat(RagService.citationsOf(hits)).containsExactly("what_is", "side_effects");
    }

    @Test
    void truncation_caveat_only_when_a_source_leaflet_is_truncated() {
        assertThat(RagService.anyTruncated(List.of(new Chunk("s", 5, "t", 0.9, true)))).isTrue();
        assertThat(RagService.anyTruncated(List.of(new Chunk("s", 5, "t", 0.9, false)))).isFalse();
        assertThat(RagService.truncationCaveat(true)).contains("truncated");
        assertThat(RagService.truncationCaveat(false)).isEmpty();
    }
}
