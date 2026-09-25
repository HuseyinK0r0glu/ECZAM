package com.eczam.medications;

import com.eczam.shared.web.ApiException;
import com.eczam.shared.web.ErrorCode;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque keyset-pagination cursor for {@code GET /medications} search results.
 *
 * <p>Unlike {@link com.eczam.shared.web.CursorCodec} (a single timestamp, for
 * newest-first lists), this endpoint's sort key depends on whether a search
 * query is present:
 * <ul>
 *   <li>{@code q} blank — alphabetical browse, sort key {@code (name, id)}</li>
 *   <li>{@code q} present — relevance ranked, sort key {@code (score DESC, id)}</li>
 * </ul>
 * Both need an {@code id} tiebreaker so paging stays stable when many rows
 * share a name or a similarity score. The cursor also records which mode
 * produced it, so decoding it against the "wrong" mode (e.g. the caller
 * changed {@code q} between requests but replayed an old cursor) is rejected
 * rather than silently seeking on the wrong key.
 */
public final class MedicationSearchCursor {

    private enum Mode { ALPHA, SIMILARITY }

    private final Mode mode;
    private final String afterName;   // ALPHA only
    private final Double afterScore;  // SIMILARITY only
    private final UUID afterId;

    private MedicationSearchCursor(Mode mode, String afterName, Double afterScore, UUID afterId) {
        this.mode = mode;
        this.afterName = afterName;
        this.afterScore = afterScore;
        this.afterId = afterId;
    }

    public static MedicationSearchCursor forAlpha(String name, UUID id) {
        return new MedicationSearchCursor(Mode.ALPHA, name, null, id);
    }

    public static MedicationSearchCursor forSimilarity(double score, UUID id) {
        return new MedicationSearchCursor(Mode.SIMILARITY, null, score, id);
    }

    public String afterName() { return afterName; }
    public Double afterScore() { return afterScore; }
    public UUID afterId() { return afterId; }

    public String encode() {
        String payload = switch (mode) {
            case ALPHA -> "A\u0001" + urlEncode(afterName) + "\u0001" + afterId;
            case SIMILARITY -> "S\u0001" + afterScore + "\u0001" + afterId;
        };
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    /** Decodes a cursor produced for the alphabetical (blank-{@code q}) browse mode. */
    public static MedicationSearchCursor decodeAlpha(String cursor) {
        return decode(cursor, Mode.ALPHA);
    }

    /** Decodes a cursor produced for the similarity-ranked (non-blank-{@code q}) mode. */
    public static MedicationSearchCursor decodeSimilarity(String cursor) {
        return decode(cursor, Mode.SIMILARITY);
    }

    private static MedicationSearchCursor decode(String cursor, Mode expected) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\u0001", -1);
            if (parts.length != 3) throw new IllegalArgumentException("malformed cursor");

            Mode mode = switch (parts[0]) {
                case "A" -> Mode.ALPHA;
                case "S" -> Mode.SIMILARITY;
                default -> throw new IllegalArgumentException("unknown cursor mode");
            };
            if (mode != expected) throw new IllegalArgumentException("cursor mode mismatch");

            UUID id = UUID.fromString(parts[2]);
            return switch (mode) {
                case ALPHA -> forAlpha(urlDecode(parts[1]), id);
                case SIMILARITY -> forSimilarity(Double.parseDouble(parts[1]), id);
            };
        } catch (Exception e) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Invalid cursor");
        }
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e); // UTF-8 is always supported
        }
    }
}
