package com.eczam.medications;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** GS1 GTIN canonicalisation — the scan join key. A wrong rule = silent miss. */
class GtinTest {

    @Test void ean13_prepends_one_zero() {
        assertThat(Gtin.canonicalize("8681030190415")).contains("08681030190415");
    }

    @Test void gtin14_is_unchanged() {
        assertThat(Gtin.canonicalize("08681030190415")).contains("08681030190415");
    }

    @Test void upc12_prepends_two_zeros() {
        assertThat(Gtin.canonicalize("012345678905")).contains("00012345678905");
    }

    @Test void ean8_pads_to_fourteen() {
        assertThat(Gtin.canonicalize("12345670")).contains("00000012345670");
    }

    @Test void trims_surrounding_whitespace() {
        assertThat(Gtin.canonicalize("  8681030190415 ")).contains("08681030190415");
    }

    @Test void non_numeric_is_empty() {
        assertThat(Gtin.canonicalize("ABC123")).isEmpty();
        assertThat(Gtin.canonicalize("868-103-0190415")).isEmpty();
    }

    @Test void odd_lengths_are_empty() {
        assertThat(Gtin.canonicalize("12345678901")).isEmpty();      // len 11
        assertThat(Gtin.canonicalize("1234567890123456")).isEmpty(); // len 16
    }

    @Test void null_and_blank_are_empty() {
        assertThat(Gtin.canonicalize(null)).isEmpty();
        assertThat(Gtin.canonicalize("   ")).isEmpty();
        assertThat(Gtin.canonicalize("")).isEmpty();
    }

    @Test void canonical_form_is_always_fourteen_digits() {
        for (String code : new String[]{"12345670", "012345678905", "8681030190415", "08681030190415"}) {
            assertThat(Gtin.canonicalize(code)).hasValueSatisfying(g -> assertThat(g).hasSize(14));
        }
    }
}
