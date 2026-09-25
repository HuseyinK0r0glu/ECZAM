package com.eczam.medications;

import com.eczam.ai.LeafletIndexer;
import com.eczam.integrations.barcode.OpenFdaClient;
import com.eczam.medications.dto.MedicationDtos.*;
import com.eczam.shared.web.ApiException;
import com.eczam.shared.web.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MedicationService {

    /** Hard ceiling on `limit`, regardless of what the caller asks for. */
    static final int MAX_LIMIT = 100;

    private final MedicationRepository repo;
    private final MedicationSearchRepository searchRepo;
    private final OpenFdaClient openFda;
    private final LeafletIndexer indexer;
    private final double minSimilarity;

    public MedicationService(MedicationRepository repo, MedicationSearchRepository searchRepo,
                              OpenFdaClient openFda, LeafletIndexer indexer,
                              @Value("${eczam.medications.search.min-similarity:0.25}") double minSimilarity) {
        this.repo = repo;
        this.searchRepo = searchRepo;
        this.openFda = openFda;
        this.indexer = indexer;
        this.minSimilarity = minSimilarity;
    }

    /**
     * One page of catalog search results: the items, the opaque cursor for
     * the next one (null when there isn't one), and the actually-applied
     * page size (the caller's requested {@code limit}, clamped).
     */
    public record SearchPage(List<MedicationView> items, String nextCursor, int effectiveLimit) {}

    /**
     * Catalog free-text search. Blank {@code q} preserves the original
     * "browse everything" behavior (alphabetical by name). A non-blank
     * {@code q} ranks by pg_trgm {@code word_similarity} — typo-tolerant,
     * and scores a short query against the best-matching word span in a
     * long catalog name rather than the whole string — dropping anything
     * below {@link #minSimilarity} so an unrelated query returns nothing.
     */
    @Transactional(readOnly = true)
    public SearchPage search(String q, String cursor, int limit) {
        int effectiveLimit = clampLimit(limit);
        boolean blank = q == null || q.isBlank();

        // Fetch one extra row to learn whether a next page exists, without a
        // separate (and racier) COUNT query.
        List<MedicationSearchRepository.Row> rows;
        if (blank) {
            MedicationSearchCursor c = MedicationSearchCursor.decodeAlpha(cursor);
            rows = searchRepo.browseAlphabetical(
                    c == null ? null : c.afterName(),
                    c == null ? null : c.afterId(),
                    effectiveLimit + 1);
        } else {
            MedicationSearchCursor c = MedicationSearchCursor.decodeSimilarity(cursor);
            rows = searchRepo.searchBySimilarity(
                    q.trim(), minSimilarity,
                    c == null ? null : c.afterScore(),
                    c == null ? null : c.afterId(),
                    effectiveLimit + 1);
        }

        boolean hasMore = rows.size() > effectiveLimit;
        List<MedicationSearchRepository.Row> page = hasMore ? rows.subList(0, effectiveLimit) : rows;

        String nextCursor = null;
        if (hasMore) {
            MedicationSearchRepository.Row last = page.get(page.size() - 1);
            nextCursor = blank
                    ? MedicationSearchCursor.forAlpha(last.name(), last.id()).encode()
                    : MedicationSearchCursor.forSimilarity(last.score(), last.id()).encode();
        }

        return new SearchPage(page.stream().map(MedicationService::toView).toList(), nextCursor, effectiveLimit);
    }

    static int clampLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    @Transactional(readOnly = true)
    public MedicationDetail get(UUID id) {
        return toDetail(load(id));
    }

    @Transactional
    public MedicationDetail create(CreateMedicationRequest req) {
        Medication m = new Medication();
        m.setName(req.name());
        m.setGenericName(req.genericName());
        m.setManufacturer(req.manufacturer());
        m.setBarcode(emptyToNull(req.barcode()));
        m.setForm(req.form());
        m.setStrength(req.strength());
        m.setLeafletRaw(req.leafletRaw());
        m.setLeafletSections(req.leafletSections());
        repo.save(m);
        scheduleIngest(m.getId());   // async background embedding (UC-010)
        return toDetail(m);
    }

    /** Barcode lookup: canonical GTIN → raw barcode → OpenFDA (create + ingest) → 404. */
    @Transactional
    public MedicationDetail lookupByBarcode(String code) {
        // A scan decodes a GS1 GTIN-14; the catalog stores mostly EAN-13. Resolve
        // both via the canonical 14-digit gtin before falling back to the raw code.
        return Gtin.canonicalize(code).flatMap(repo::findByGtin)
                .or(() -> repo.findByBarcode(code))
                .map(MedicationService::toDetail)
                .orElseGet(() -> openFda.lookupByBarcode(code)
                        .map(m -> {
                            repo.save(m);
                            scheduleIngest(m.getId());   // async background embedding (UC-010)
                            return toDetail(m);
                        })
                        .orElseThrow(() -> new ApiException(
                                org.springframework.http.HttpStatus.NOT_FOUND,
                                ErrorCode.BARCODE_NOT_FOUND,
                                "Barcode not found; please add the medication manually")));
    }

    @Transactional(readOnly = true)
    public LeafletSearchResult searchLeaflet(UUID id, String q) {
        LeafletSections s = load(id).getLeafletSections();
        List<LeafletSearchHit> hits = new ArrayList<>();
        if (s != null && q != null && !q.isBlank()) {
            String needle = q.toLowerCase();
            addHit(hits, "dosage", s.dosage(), needle);
            addHit(hits, "side_effects", s.sideEffects(), needle);
            addHit(hits, "contraindications", s.contraindications(), needle);
            addHit(hits, "storage", s.storage(), needle);
            addHit(hits, "interactions", s.interactions(), needle);
            addHit(hits, "missed_dose", s.missedDose(), needle);
        }
        return new LeafletSearchResult(hits);
    }

    Medication load(UUID id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Medication not found"));
    }

    /**
     * Kick off async leaflet ingestion only after the surrounding transaction commits,
     * so the indexer's own transaction can actually see the new medication row
     * (otherwise the async thread may run before the insert is visible and skip it).
     */
    private void scheduleIngest(UUID medicationId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { indexer.ingest(medicationId); }
            });
        } else {
            indexer.ingest(medicationId);
        }
    }

    private static void addHit(List<LeafletSearchHit> hits, String section, String text, String needle) {
        if (text == null) return;
        int idx = text.toLowerCase().indexOf(needle);
        if (idx >= 0) {
            int start = Math.max(0, idx - 60);
            int end = Math.min(text.length(), idx + needle.length() + 60);
            hits.add(new LeafletSearchHit(section, "…" + text.substring(start, end).trim() + "…"));
        }
    }

    static MedicationView toView(MedicationSearchRepository.Row r) {
        return new MedicationView(r.id().toString(), r.name(), r.genericName(),
                r.manufacturer(), r.barcode(), r.form(), r.strength(), r.vectorIndexed());
    }
    static MedicationDetail toDetail(Medication m) {
        return new MedicationDetail(m.getId().toString(), m.getName(), m.getGenericName(),
                m.getManufacturer(), m.getBarcode(), m.getForm(), m.getStrength(),
                m.getLeafletSections(), m.isVectorIndexed());
    }
    private static String emptyToNull(String s) { return s == null || s.isBlank() ? null : s; }
}
