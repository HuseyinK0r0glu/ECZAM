package com.eczam.medications;

import java.util.Optional;

/**
 * GS1 GTIN canonicalisation.
 *
 * <p>A DataMatrix scan yields a 14-digit GTIN (AI 01); the seed dataset stores
 * mostly EAN-13 barcodes. Both sides MUST be normalised to the same 14-digit
 * form or lookups silently miss. Rule: keep digits only, then left-zero-pad to
 * 14 (EAN-13 → prepend {@code 0}, UPC-12 → prepend {@code 00}, GTIN-14 stays).
 *
 * <p>Inputs that are not 8/12/13/14 all-digit codes (e.g. the handful of
 * non-numeric or odd-length barcodes in the source) yield {@link Optional#empty()}
 * — such products are stored with a {@code null} gtin and are search-only,
 * never scan-matchable.
 */
public final class Gtin {

    private Gtin() {}

    public static Optional<String> canonicalize(String raw) {
        if (raw == null) return Optional.empty();
        String digits = raw.trim();
        if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) return Optional.empty();
        int len = digits.length();
        if (len != 8 && len != 12 && len != 13 && len != 14) return Optional.empty();
        return Optional.of("0".repeat(14 - len) + digits);
    }
}
