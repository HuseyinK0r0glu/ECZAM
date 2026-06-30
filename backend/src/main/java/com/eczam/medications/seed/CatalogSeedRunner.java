package com.eczam.medications.seed;

import com.eczam.medications.Gtin;
import com.eczam.medications.LeafletSections;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Stage A of the medications seed (see {@code plans/medications-schema-plan.md}).
 *
 * <p>One-time, idempotent import of the Tip-Atlası {@code ilac} JSON export into
 * the {@code medications} catalog. Streams the ~55 MB file row-by-row, canonical-
 * ises GTINs, deduplicates, cleans placeholders/categories, and parses real
 * leaflets into {@code leaflet_sections}. Re-runs add nothing (upsert on the
 * unique key). Embedding is Stage B ({@code LeafletEmbedRunner}).
 *
 * <p>Enabled with {@code eczam.seed.catalog.enabled=true} and a path in
 * {@code eczam.seed.catalog.file}.
 */
@Component
@ConditionalOnProperty(name = "eczam.seed.catalog.enabled", havingValue = "true")
public class CatalogSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogSeedRunner.class);
    private static final int BATCH = 500;

    private static final String UPSERT_BY_GTIN = """
            INSERT INTO medications
              (name, generic_name, barcode, gtin, atc_code, atc_group, active_ingredient,
               category_path, leaflet_raw, leaflet_sections, leaflet_truncated, leaflet_hash, vector_indexed)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?, ?, FALSE)
            ON CONFLICT (gtin) DO NOTHING
            """;

    // Rows whose barcode is not a valid GTIN get gtin = NULL (NULLs never
    // conflict), so idempotency for them keys on the unique barcode instead.
    private static final String UPSERT_BY_BARCODE = """
            INSERT INTO medications
              (name, generic_name, barcode, gtin, atc_code, atc_group, active_ingredient,
               category_path, leaflet_raw, leaflet_sections, leaflet_truncated, leaflet_hash, vector_indexed)
            VALUES (?, ?, ?, NULL, ?, ?, ?, ?::jsonb, ?, ?::jsonb, ?, ?, FALSE)
            ON CONFLICT (barcode) DO NOTHING
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final String filePath;

    public CatalogSeedRunner(JdbcTemplate jdbc, ObjectMapper mapper,
                             @Value("${eczam.seed.catalog.file:}") String filePath) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.filePath = filePath;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        File file = new File(filePath);
        if (filePath.isBlank() || !file.isFile()) {
            log.error("Catalog seed enabled but file not found: '{}'. Skipping.", filePath);
            return;
        }
        log.info("Catalog seed: importing from {}", file.getAbsolutePath());

        var withGtin = new ArrayList<Object[]>();
        var withoutGtin = new ArrayList<Object[]>();
        Map<String, String> seenGtin = new HashMap<>();   // gtin -> name, for divergence logging
        int total = 0, realLeaflets = 0, nullGtin = 0, dupSkipped = 0;

        try (JsonParser p = mapper.getFactory().createParser(file)) {
            advanceToDataArray(p);
            while (p.nextToken() != JsonToken.END_ARRAY && p.currentToken() != null) {
                JsonNode row = mapper.readTree(p);
                total++;

                String barcode = text(row, "barcode");
                String name = text(row, "Product_Name");
                if (name == null) name = "(isimsiz ilaç)";
                String gtin = Gtin.canonicalize(barcode).orElse(null);

                if (gtin != null) {
                    String prevName = seenGtin.putIfAbsent(gtin, name);
                    if (prevName != null) {
                        dupSkipped++;
                        if (!prevName.equals(name)) {   // never happens in current data, but observable
                            log.warn("Duplicate GTIN {} with DIVERGING name: '{}' vs '{}'", gtin, prevName, name);
                        }
                        continue;   // skip in-run duplicate; DB upsert also guards re-runs
                    }
                } else {
                    nullGtin++;
                }

                String activeIngredient = SourceText.cleanActiveIngredient(text(row, "Active_Ingredient"));
                List<String> categoryPath = SourceText.cleanCategoryPath(
                        text(row, "Category_1"), text(row, "Category_2"), text(row, "Category_3"),
                        text(row, "Category_4"), text(row, "Category_5"));
                String atcCode = blankToNull(text(row, "ATC_code"));

                String description = text(row, "Description");
                boolean realLeaflet = SourceText.isRealLeaflet(description);
                String leafletRaw = null, leafletSectionsJson = null, leafletHash = null;
                boolean truncated = false;
                if (realLeaflet) {
                    realLeaflets++;
                    leafletRaw = description;
                    truncated = SourceText.isTruncated(description);
                    leafletHash = sha256(description);
                    LeafletSections sections = LeafletParser.toLeafletSections(LeafletParser.parse(description));
                    leafletSectionsJson = mapper.writeValueAsString(sections);
                }
                String categoryJson = categoryPath == null ? null : mapper.writeValueAsString(categoryPath);
                String atcGroup = SourceText.atcGroup(atcCode);

                if (gtin != null) {
                    withGtin.add(new Object[]{name, activeIngredient, barcode, gtin, atcCode, atcGroup,
                            activeIngredient, categoryJson, leafletRaw, leafletSectionsJson, truncated, leafletHash});
                } else {
                    withoutGtin.add(new Object[]{name, activeIngredient, barcode, atcCode, atcGroup,
                            activeIngredient, categoryJson, leafletRaw, leafletSectionsJson, truncated, leafletHash});
                }

                if (withGtin.size() >= BATCH) flush(UPSERT_BY_GTIN, withGtin);
                if (withoutGtin.size() >= BATCH) flush(UPSERT_BY_BARCODE, withoutGtin);
            }
        }
        flush(UPSERT_BY_GTIN, withGtin);
        flush(UPSERT_BY_BARCODE, withoutGtin);

        log.info("Catalog seed done: read={}, unique-gtin={}, null-gtin={}, in-run-dups={}, real-leaflets={}",
                total, seenGtin.size(), nullGtin, dupSkipped, realLeaflets);
    }

    /** Positions the parser just before the first element of the rows {@code "data"} array. */
    private static void advanceToDataArray(JsonParser p) throws Exception {
        while (p.nextToken() != null) {
            if (p.currentToken() == JsonToken.FIELD_NAME && "data".equals(p.currentName())) {
                if (p.nextToken() == JsonToken.START_ARRAY) return;
            }
        }
        throw new IllegalStateException("No 'data' array found in export — unexpected file format");
    }

    private void flush(String sql, List<Object[]> batch) {
        if (batch.isEmpty()) return;
        jdbc.batchUpdate(sql, batch);
        batch.clear();
    }

    private static String text(JsonNode row, String field) {
        JsonNode n = row.get(field);
        return (n == null || n.isNull()) ? null : n.asText();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
