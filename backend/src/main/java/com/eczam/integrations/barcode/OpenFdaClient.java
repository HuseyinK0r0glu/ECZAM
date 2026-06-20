package com.eczam.integrations.barcode;

import com.eczam.medications.LeafletSections;
import com.eczam.medications.Medication;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** OpenFDA drug-label fallback (brief §9.2). */
@Component
public class OpenFdaClient {

    private final RestClient http = RestClient.builder()
            .baseUrl("https://api.fda.gov").build();

    @SuppressWarnings("unchecked")
    public Optional<Medication> lookupByBarcode(String code) {
        try {
            Map<String, Object> body = http.get()
                    .uri(uri -> uri.path("/drug/label.json")
                            .queryParam("search", "openfda.upc_udi_di:" + code)
                            .queryParam("limit", 1).build())
                    .retrieve().body(Map.class);

            List<Map<String, Object>> results = body == null ? null : (List<Map<String, Object>>) body.get("results");
            if (results == null || results.isEmpty()) return Optional.empty();

            Map<String, Object> r = results.get(0);
            Map<String, Object> openfda = (Map<String, Object>) r.getOrDefault("openfda", Map.of());

            Medication m = new Medication();
            m.setName(firstOf(openfda.get("brand_name"), "Unknown"));
            m.setGenericName(firstOf(openfda.get("generic_name"), null));
            m.setManufacturer(firstOf(openfda.get("manufacturer_name"), null));
            m.setBarcode(code);
            m.setLeafletRaw(joinAll(r));
            m.setLeafletSections(new LeafletSections(
                    text(r.get("dosage_and_administration")),
                    text(r.get("adverse_reactions")),
                    text(r.get("contraindications")),
                    text(r.get("how_supplied_storage_and_handling")),
                    text(r.get("drug_interactions")),
                    text(r.get("dosage_and_administration"))));
            return Optional.of(m);
        } catch (Exception e) {
            return Optional.empty(); // treated as a miss → manual entry
        }
    }

    @SuppressWarnings("unchecked")
    private static String firstOf(Object v, String fallback) {
        if (v instanceof List<?> l && !l.isEmpty()) return String.valueOf(l.get(0));
        return fallback;
    }
    @SuppressWarnings("unchecked")
    private static String text(Object v) {
        if (v instanceof List<?> l && !l.isEmpty()) return String.valueOf(l.get(0));
        return v == null ? null : String.valueOf(v);
    }
    private static String joinAll(Map<String, Object> r) {
        StringBuilder sb = new StringBuilder();
        r.forEach((k, v) -> { if (v instanceof List<?> l && !l.isEmpty()) sb.append(k).append(": ").append(l.get(0)).append("\n\n"); });
        return sb.toString();
    }
}
