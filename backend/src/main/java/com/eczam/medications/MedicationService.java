package com.eczam.medications;

import com.eczam.ai.LeafletIndexer;
import com.eczam.integrations.barcode.OpenFdaClient;
import com.eczam.medications.dto.MedicationDtos.*;
import com.eczam.shared.web.ApiException;
import com.eczam.shared.web.CursorCodec;
import com.eczam.shared.web.ErrorCode;
import com.eczam.shared.web.Meta;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MedicationService {

    private final MedicationRepository repo;
    private final OpenFdaClient openFda;
    private final LeafletIndexer indexer;

    public MedicationService(MedicationRepository repo, OpenFdaClient openFda, LeafletIndexer indexer) {
        this.repo = repo;
        this.openFda = openFda;
        this.indexer = indexer;
    }

    @Transactional(readOnly = true)
    public List<MedicationView> search(String q, int limit) {
        return repo.search(q == null || q.isBlank() ? null : q, PageRequest.of(0, limit))
                .map(MedicationService::toView).getContent();
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

    /** Barcode lookup: local → OpenFDA (create + ingest) → 404. */
    @Transactional
    public MedicationDetail lookupByBarcode(String code) {
        return repo.findByBarcode(code)
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

    public Meta cursorMeta(int limit) { return new Meta(null, limit); }

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

    static MedicationView toView(Medication m) {
        return new MedicationView(m.getId().toString(), m.getName(), m.getGenericName(),
                m.getManufacturer(), m.getBarcode(), m.getForm(), m.getStrength(), m.isVectorIndexed());
    }
    static MedicationDetail toDetail(Medication m) {
        return new MedicationDetail(m.getId().toString(), m.getName(), m.getGenericName(),
                m.getManufacturer(), m.getBarcode(), m.getForm(), m.getStrength(),
                m.getLeafletSections(), m.isVectorIndexed());
    }
    private static String emptyToNull(String s) { return s == null || s.isBlank() ? null : s; }
}
