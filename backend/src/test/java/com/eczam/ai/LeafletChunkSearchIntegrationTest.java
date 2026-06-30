package com.eczam.ai;

import com.eczam.AbstractIntegrationTest;
import com.eczam.medications.Medication;
import com.eczam.medications.MedicationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LeafletChunkSearchIntegrationTest extends AbstractIntegrationTest {

    @Autowired LeafletChunkRepository chunks;
    @Autowired MedicationRepository medications;

    private static final int DIM = 1536;

    /** One-hot 1536-dim vector with `value` at `index`. */
    private static float[] oneHot(int index, float value) {
        float[] v = new float[DIM];
        v[index] = value;
        return v;
    }

    private UUID newMedication() {
        Medication m = new Medication();
        m.setName("Vec Med " + UUID.randomUUID());
        return medications.save(m).getId();
    }

    @Test
    void cosine_search_returns_nearest_first_and_respects_medication_filter() {
        UUID med = newMedication();
        UUID otherMed = newMedication();

        chunks.insert(med, "dosage", 3, "Günde iki kez bir tablet.", 0, 25, 6, "tr", oneHot(0, 1f), 0);
        chunks.insert(med, "storage", 5, "Oda sıcaklığında saklayın.", 0, 26, 6, "tr", oneHot(1, 1f), 1);
        chunks.insert(med, "side_effects", 4, "Baş ağrısı görülebilir.", 0, 23, 5, "tr", oneHot(2, 1f), 2);

        // Query closest to the "dosage" vector (index 0).
        float[] query = oneHot(0, 0.9f);
        query[1] = 0.1f;

        List<LeafletChunkRepository.Chunk> results = chunks.search(query, med, 3);
        assertThat(results).hasSize(3);
        assertThat(results.get(0).sectionName()).isEqualTo("dosage");
        // Cosine similarity score for the nearest should be high (~1.0).
        assertThat(results.get(0).score()).isGreaterThan(0.9);

        // Scoping to a different medication yields nothing.
        assertThat(chunks.search(query, otherMed, 3)).isEmpty();

        // Unscoped search (null filter) still finds this medication's chunks.
        assertThat(chunks.search(query, null, 10))
                .extracting(LeafletChunkRepository.Chunk::sectionName)
                .contains("dosage", "storage", "side_effects");
    }
}
