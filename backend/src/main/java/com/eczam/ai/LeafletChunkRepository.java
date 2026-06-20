package com.eczam.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class LeafletChunkRepository {

    public record Chunk(String sectionName, String chunkText, double score) {}

    private final JdbcTemplate jdbc;
    public LeafletChunkRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void insert(UUID medicationId, String sectionName, String chunkText, float[] embedding, int chunkIndex) {
        jdbc.update("""
                INSERT INTO leaflet_chunks (medication_id, section_name, chunk_text, embedding, chunk_index)
                VALUES (?, ?, ?, ?::vector, ?)
                """, medicationId, sectionName, chunkText, toVectorLiteral(embedding), chunkIndex);
    }

    /** Top-k by cosine similarity; optional medication filter. (<=> is pgvector cosine distance.) */
    public List<Chunk> search(float[] queryEmbedding, UUID medicationId, int k) {
        String vec = toVectorLiteral(queryEmbedding);
        return jdbc.query("""
                SELECT section_name, chunk_text, 1 - (embedding <=> ?::vector) AS score
                FROM leaflet_chunks
                WHERE (?::uuid IS NULL OR medication_id = ?::uuid)
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """,
                (rs, n) -> new Chunk(rs.getString("section_name"), rs.getString("chunk_text"), rs.getDouble("score")),
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
