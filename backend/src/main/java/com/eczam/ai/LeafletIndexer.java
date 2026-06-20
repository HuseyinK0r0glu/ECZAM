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
