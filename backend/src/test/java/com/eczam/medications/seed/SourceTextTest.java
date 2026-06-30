package com.eczam.medications.seed;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Data-driven cleaning rules for the Tip-Atlası source export. */
class SourceTextTest {

    private static String longText(int len) {
        return "x".repeat(len);
    }

    @Test void real_leaflet_detection() {
        assertThat(SourceText.isRealLeaflet(null)).isFalse();
        assertThat(SourceText.isRealLeaflet("")).isFalse();
        assertThat(SourceText.isRealLeaflet("-")).isFalse();
        assertThat(SourceText.isRealLeaflet("İçerik bulunamadı.")).isFalse();
        assertThat(SourceText.isRealLeaflet("İkinci siteye ait içerik bulunamadı.")).isFalse();
        // Below the 150-char floor → treated as junk even if not a known placeholder.
        assertThat(SourceText.isRealLeaflet("Kısa bir açıklama.")).isFalse();
        // A genuine, long leaflet.
        assertThat(SourceText.isRealLeaflet(longText(200))).isTrue();
    }

    @Test void truncation_flag_near_ceiling() {
        assertThat(SourceText.isTruncated(longText(32_700))).isTrue();
        assertThat(SourceText.isTruncated(longText(5_000))).isFalse();
        assertThat(SourceText.isTruncated(null)).isFalse();
    }

    @Test void active_ingredient_placeholders_become_null() {
        assertThat(SourceText.cleanActiveIngredient(null)).isNull();
        assertThat(SourceText.cleanActiveIngredient("-")).isNull();
        assertThat(SourceText.cleanActiveIngredient("Etken maddesi bilgisi bulunamadı.")).isNull();
        assertThat(SourceText.cleanActiveIngredient(" Parasetamol ")).isEqualTo("Parasetamol");
    }

    @Test void category_path_drops_sentinels_and_consecutive_dupes() {
        assertThat(SourceText.cleanCategoryPath(
                "Kas İskelet Sistemi", "-", "Yok", "Antienflamatuar", "Antienflamatuar", "0"))
                .containsExactly("Kas İskelet Sistemi", "Antienflamatuar");
        assertThat(SourceText.cleanCategoryPath("Yok", "-", "0", "")).isNull();
        assertThat(SourceText.cleanCategoryPath()).isNull();
    }

    @Test void atc_group_is_first_letter_uppercased() {
        assertThat(SourceText.atcGroup("M01AE01")).isEqualTo("M");
        assertThat(SourceText.atcGroup("m01")).isEqualTo("M");
        assertThat(SourceText.atcGroup(null)).isNull();
        assertThat(SourceText.atcGroup("  ")).isNull();
        assertThat(SourceText.atcGroup("1ABC")).isNull();
    }
}
