package com.eczam.medications;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The `limit` query param's docs claim "default 20, max 100" — this pins down
 * that the clamp is actually enforced (it previously was not).
 */
class MedicationServiceLimitClampTest {

    @Test void a_normal_limit_passes_through_unchanged() {
        assertThat(MedicationService.clampLimit(20)).isEqualTo(20);
        assertThat(MedicationService.clampLimit(1)).isEqualTo(1);
        assertThat(MedicationService.clampLimit(100)).isEqualTo(100);
    }

    @Test void limits_above_the_documented_maximum_are_clamped_down_to_it() {
        assertThat(MedicationService.clampLimit(101)).isEqualTo(100);
        assertThat(MedicationService.clampLimit(500)).isEqualTo(100);
        assertThat(MedicationService.clampLimit(Integer.MAX_VALUE)).isEqualTo(100);
    }

    @Test void non_positive_limits_are_clamped_up_to_1() {
        assertThat(MedicationService.clampLimit(0)).isEqualTo(1);
        assertThat(MedicationService.clampLimit(-5)).isEqualTo(1);
    }
}
