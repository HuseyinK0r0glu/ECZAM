package com.eczam.logs;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit test for {@link AdherenceService#isStreakDay}, the day-classification
 * rule behind the adherence streak endpoint. Mirrors the style of
 * {@code ScheduleIsDueTest} — plain JUnit, no Spring context.
 */
class AdherenceStreakRuleTest {

    @Test void no_doses_due_counts_as_a_streak_day() {
        assertThat(AdherenceService.isStreakDay(0, 0)).isTrue();
    }

    @Test void all_expected_doses_logged_counts_as_a_streak_day() {
        assertThat(AdherenceService.isStreakDay(2, 2)).isTrue();
    }

    @Test void more_logged_than_expected_still_counts() {
        // Can't happen in practice (taken is capped at expected before this is
        // called), but the rule itself should be tolerant.
        assertThat(AdherenceService.isStreakDay(2, 3)).isTrue();
    }

    @Test void partial_or_missed_doses_break_the_streak() {
        assertThat(AdherenceService.isStreakDay(2, 1)).isFalse();
        assertThat(AdherenceService.isStreakDay(1, 0)).isFalse();
    }
}
