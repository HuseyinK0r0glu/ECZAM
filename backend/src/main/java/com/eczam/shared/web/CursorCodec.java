package com.eczam.shared.web;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;

/** Opaque cursor: base64 of an ISO-8601 timestamp (newest-first lists). */
public final class CursorCodec {
    public static String encode(OffsetDateTime ts) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(ts.toString().getBytes(StandardCharsets.UTF_8));
    }
    public static OffsetDateTime decode(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            return OffsetDateTime.parse(raw);
        } catch (Exception e) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Invalid cursor");
        }
    }
    private CursorCodec() {}
}
