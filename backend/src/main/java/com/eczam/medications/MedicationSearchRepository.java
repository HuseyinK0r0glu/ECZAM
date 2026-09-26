package com.eczam.medications;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Backs {@code GET /medications?q=} (and {@code ?category=}). Split out from
 * {@link MedicationRepository} (a plain Spring Data interface) because both
 * query shapes here need keyset (seek) pagination on a sort key that isn't a
 * mapped entity property — a computed trigram similarity score, or a
 * (name, id) pair — which a derived/JPQL query can't express cleanly. Same
 * pattern as {@link com.eczam.ai.LeafletChunkRepository} (a plain JDBC
 * repository for a Postgres-extension-specific query: pgvector's cosine ops
 * there, pg_trgm's word_similarity here).
 */
@Repository
public class MedicationSearchRepository {

    /** One catalog row plus its relevance score (null in alphabetical mode). */
    public record Row(UUID id, String name, String genericName, String manufacturer,
                       String barcode, String form, String strength,
                       List<String> categoryPath, boolean vectorIndexed,
                       Double score) {}

    private static final String COLUMNS =
            "id, name, generic_name, manufacturer, barcode, form, strength, category_path, vector_indexed";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public MedicationSearchRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /**
     * No-query browse: alphabetical by name, id tiebreak. Preserves the
     * endpoint's pre-existing behavior for a null/blank {@code q}. {@code category}
     * optionally narrows to rows whose top-level {@code category_path} entry
     * exactly matches (see {@code GET /medications/categories}).
     */
    public List<Row> browseAlphabetical(String category, String afterName, UUID afterId, int limit) {
        return jdbc.query("""
                SELECT %s FROM medications
                WHERE (CAST(? AS text) IS NULL OR category_path->>0 = CAST(? AS text))
                  AND (
                        CAST(? AS text) IS NULL
                        OR name > CAST(? AS text)
                        OR (name = CAST(? AS text) AND id > CAST(? AS uuid))
                      )
                ORDER BY name ASC, id ASC
                LIMIT ?
                """.formatted(COLUMNS),
                this::mapRow,
                category, category, afterName, afterName, afterName, afterId, limit);
    }

    /**
     * Typo-tolerant ranked search. Scores each row by
     * {@code word_similarity(q, field)} — the best-matching contiguous word
     * span in the (typically longer) catalog name/generic-name/active-
     * ingredient, rather than whole-string {@code similarity()} — so a short
     * hand-typed query like "aspirin" still scores well against a verbose
     * catalog entry like "ASPIRIN 100 MG 20 TABLET". Rows below
     * {@code minSimilarity} are dropped so an unrelated query returns nothing
     * rather than noise. {@code category} optionally narrows to rows whose
     * top-level {@code category_path} entry exactly matches. Ordered by
     * score DESC, id ASC (tiebreak) for stable keyset paging.
     */
    public List<Row> searchBySimilarity(String q, String category, double minSimilarity,
                                         Double afterScore, UUID afterId, int limit) {
        return jdbc.query("""
                SELECT * FROM (
                    SELECT %s,
                           GREATEST(
                               word_similarity(?, name),
                               word_similarity(?, COALESCE(generic_name, '')),
                               word_similarity(?, COALESCE(active_ingredient, ''))
                           ) AS score
                    FROM medications
                    WHERE CAST(? AS text) IS NULL OR category_path->>0 = CAST(? AS text)
                ) ranked
                WHERE score >= ?
                  AND (
                        CAST(? AS double precision) IS NULL
                        OR score < CAST(? AS double precision)
                        OR (score = CAST(? AS double precision) AND id > CAST(? AS uuid))
                      )
                ORDER BY score DESC, id ASC
                LIMIT ?
                """.formatted(COLUMNS),
                this::mapRankedRow,
                q, q, q, category, category, minSimilarity, afterScore, afterScore, afterScore, afterId, limit);
    }

    private Row mapRow(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Row(
                UUID.fromString(rs.getString("id")),
                rs.getString("name"),
                rs.getString("generic_name"),
                rs.getString("manufacturer"),
                rs.getString("barcode"),
                rs.getString("form"),
                rs.getString("strength"),
                parseCategoryPath(rs.getString("category_path")),
                rs.getBoolean("vector_indexed"),
                null);
    }

    private Row mapRankedRow(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        Row base = mapRow(rs, n);
        return new Row(base.id(), base.name(), base.genericName(), base.manufacturer(),
                base.barcode(), base.form(), base.strength(), base.categoryPath(), base.vectorIndexed(),
                rs.getDouble("score"));
    }

    private List<String> parseCategoryPath(String json) {
        if (json == null) return null;
        try {
            return mapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return null; // malformed/unexpected shape — degrade to "no category" rather than fail the search
        }
    }
}
