package com.eczam.ai;

import com.eczam.medications.MedicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Stage B of the medications seed (see {@code plans/medications-schema-plan.md}).
 *
 * <p>Embeds every real leaflet that is not yet indexed, one medication at a time
 * so the run is resumable (each success flips {@code vector_indexed = true}) and
 * gentle on the embedding API. Re-running only processes what remains.
 *
 * <p>Enabled with {@code eczam.seed.embeddings.enabled=true}. Requires
 * {@code OPENAI_API_KEY}. Run after the Stage A catalog import.
 */
@Component
@ConditionalOnProperty(name = "eczam.seed.embeddings.enabled", havingValue = "true")
public class LeafletEmbedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LeafletEmbedRunner.class);

    private final MedicationRepository medications;
    private final LeafletIndexer indexer;

    public LeafletEmbedRunner(MedicationRepository medications, LeafletIndexer indexer) {
        this.medications = medications;
        this.indexer = indexer;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<UUID> ids = medications.findUnindexedLeafletIds();
        log.info("Leaflet embed seed: {} medications to index", ids.size());

        int done = 0, failed = 0;
        for (UUID id : ids) {
            try {
                indexer.ingestOne(id);
                done++;
            } catch (Exception e) {
                failed++;
                log.error("Embed failed for {}: {}", id, e.getMessage());
            }
            if ((done + failed) % 100 == 0) {
                log.info("Leaflet embed progress: {}/{} (failed={})", done + failed, ids.size(), failed);
            }
        }
        log.info("Leaflet embed seed done: indexed={}, failed={}", done, failed);
    }
}
