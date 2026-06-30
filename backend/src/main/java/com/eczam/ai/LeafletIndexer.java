package com.eczam.ai;

import com.eczam.medications.LeafletSections;
import com.eczam.medications.Medication;
import com.eczam.medications.MedicationRepository;
import com.eczam.medications.seed.LeafletParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Leaflet → chunks → embeddings (brief §8.2).
 *
 * <p>Primary path is <b>section-aware</b>: the raw Turkish leaflet is split into
 * its numbered <i>Kullanma Talimatı</i> sections ({@link LeafletParser}), and each
 * section is then size-split into overlapping chunks. This keeps the leaflet
 * section attached to every chunk so the assistant can cite it (CLAUDE.md §7).
 * Falls back to fixed-size chunking of the raw text, then to the structured
 * {@link LeafletSections} fields (e.g. OpenFDA-sourced rows with no raw text).
 */
@Service
public class LeafletIndexer {

    private static final Logger log = LoggerFactory.getLogger(LeafletIndexer.class);
    private static final int CHUNK_CHARS = 1800;          // ~512 tokens
    private static final int OVERLAP = 270;               // ~15%
    private static final String LANG = "tr";

    private final EmbeddingClient embeddings;
    private final LeafletChunkRepository chunks;
    private final MedicationRepository medications;

    public LeafletIndexer(EmbeddingClient embeddings, LeafletChunkRepository chunks, MedicationRepository medications) {
        this.embeddings = embeddings; this.chunks = chunks; this.medications = medications;
    }

    /** Async single-medication ingest (used after catalog create / OpenFDA lookup). */
    @Async
    @Transactional
    public void ingest(UUID medicationId) {
        index(medicationId);
    }

    /** Synchronous ingest for the Stage B batch seed runner (resumable, ordered). */
    @Transactional
    public void ingestOne(UUID medicationId) {
        index(medicationId);
    }

    private void index(UUID medicationId) {
        Medication med = medications.findById(medicationId).orElse(null);
        if (med == null) return;
        try {
            chunks.deleteByMedication(medicationId);   // clean slate → idempotent re-embed
            int index = 0;
            String raw = med.getLeafletRaw();

            if (raw != null && !raw.isBlank()) {
                List<LeafletParser.Block> blocks = LeafletParser.parse(raw);
                if (!blocks.isEmpty()) {
                    for (LeafletParser.Block b : blocks) {
                        for (SubChunk sc : split(b.text())) {
                            embedAndStore(medicationId, b.key(), b.ordinal(),
                                    sc.text(), b.charStart() + sc.localStart(), index++);
                        }
                    }
                } else {
                    // Unstructured leaflet (~1% of rows): fixed-size over the whole text.
                    for (SubChunk sc : split(raw)) {
                        embedAndStore(medicationId, "full", null, sc.text(), sc.localStart(), index++);
                    }
                }
            } else if (med.getLeafletSections() != null) {
                for (Map.Entry<String, String> e : sectionsOf(med.getLeafletSections()).entrySet()) {
                    if (e.getValue() == null || e.getValue().isBlank()) continue;
                    for (SubChunk sc : split(e.getValue())) {
                        embedAndStore(medicationId, e.getKey(), null, sc.text(), null, index++);
                    }
                }
            } else {
                return;   // nothing to index → leave vector_indexed = false
            }

            med.setVectorIndexed(true);
        } catch (Exception ex) {
            log.error("Leaflet ingestion failed for {}: {}", medicationId, ex.getMessage());
            // leave vector_indexed = false → assistant declines gracefully for this med
        }
    }

    private void embedAndStore(UUID medId, String section, Integer ordinal,
                               String text, Integer charStart, int chunkIndex) {
        chunks.insert(medId, section, ordinal, text, charStart,
                text.length(), text.length() / 4, LANG, embeddings.embed(text), chunkIndex);
    }

    private record SubChunk(String text, int localStart) {}

    private static List<SubChunk> split(String text) {
        List<SubChunk> out = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int end = Math.min(text.length(), i + CHUNK_CHARS);
            out.add(new SubChunk(text.substring(i, end), i));
            if (end == text.length()) break;
            i = end - OVERLAP;
        }
        return out;
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
}
