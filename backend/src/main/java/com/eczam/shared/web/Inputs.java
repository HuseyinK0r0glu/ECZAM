package com.eczam.shared.web;

import java.util.UUID;

/** Parsing helpers that turn malformed request values into 422s instead of 500s. */
public final class Inputs {
    private Inputs() {}

    public static UUID uuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, field + " must be a valid UUID");
        }
    }

    /** Null-tolerant variant for optional id fields. */
    public static UUID uuidOrNull(String value, String field) {
        return value == null ? null : uuid(value, field);
    }
}
