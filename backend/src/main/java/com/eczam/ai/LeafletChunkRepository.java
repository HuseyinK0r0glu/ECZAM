package com.eczam.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class LeafletChunkRepository {

    public record Chunk(String sectionName, Integer sectionOrdinal, String chunkText,
                        double score, boolean truncated) {}

    private final JdbcTemplate jdbc;
    public LeafletChunkRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(UUID medicationId, String sectionName, Integer sectionOrdinal,
                       String chunkText, Integer charStart, Integer charLen, Integer tokenCount,
                       String sourceLang, float[] embedding, int chunkIndex) {
        jdbc.update("""
                INSERT INTO leaflet_chunks
                  (medication_id, section_name, section_ordinal, chunk_text, char_start, char_len,
                   token_count, source_lang, embedding, chunk_index)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::vector, ?)
                """, medicationId, sectionName, sectionOrdinal, chunkText, charStart, charLen,
                tokenCount, sourceLang, toVectorLiteral(embedding), chunkIndex);
    }

    /** Removes all chunks for a medication so a re-embed starts clean (idempotent). */
    public void deleteByMedication(UUID medicationId) {
        jdbc.update("DELETE FROM leaflet_chunks WHERE medication_id = ?", medicationId);
    }

    /** Top-k by cosine similarity; optional medication filter. (<=> is pgvector cosine distance.) */
    public List<Chunk> search(float[] queryEmbedding, UUID medicationId, int k) {
        String vec = toVectorLiteral(queryEmbedding);
        return jdbc.query("""
                SELECT lc.section_name, lc.section_ordinal, lc.chunk_text,
                       1 - (lc.embedding <=> ?::vector) AS score,
                       COALESCE(m.leaflet_truncated, false) AS truncated
                FROM leaflet_chunks lc
                JOIN medications m ON m.id = lc.medication_id
                WHERE (?::uuid IS NULL OR lc.medication_id = ?::uuid)
                ORDER BY lc.embedding <=> ?::vector
                LIMIT ?
                """,
                (rs, n) -> new Chunk(
                        rs.getString("section_name"),
                        (Integer) rs.getObject("section_ordinal"),
                        rs.getString("chunk_text"),
                        rs.getDouble("score"),
                        rs.getBoolean("truncated")),
                vec,
                medicationId == null ? null : medicationId.toString(),
                medicationId == null ? null : medicationId.toString(),
                vec, k);
    }

    private static String toVectorLiteral(float[] v) {
        return "[" + java.util.stream.IntStream.range(0, v.length)
                .mapToObj(i -> Float.toString(v[i])).collect(Collectors.joining(",")) + "]";
    }
}
