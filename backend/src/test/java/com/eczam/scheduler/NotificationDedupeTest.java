package com.eczam.scheduler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDedupeTest {

    @Test void firstTimeForMinute_is_true_once_then_false() {
        NotificationDedupe d = new NotificationDedupe();
        String key = "dose:abc:2026-06-19T08:00";
        assertThat(d.firstTimeForMinute(key)).isTrue();
        assertThat(d.firstTimeForMinute(key)).isFalse();
        assertThat(d.firstTimeForMinute(key)).isFalse();
    }

    @Test void distinct_keys_are_independent() {
        NotificationDedupe d = new NotificationDedupe();
        assertThat(d.firstTimeForMinute("a")).isTrue();
        assertThat(d.firstTimeForMinute("b")).isTrue();
        assertThat(d.firstTimeForMinute("a")).isFalse();
    }

    @Test void firstTimeToday_dedupes_within_the_day() {
        NotificationDedupe d = new NotificationDedupe();
        String key = "low:um-1:2026-06-19";
        assertThat(d.firstTimeToday(key)).isTrue();
        assertThat(d.firstTimeToday(key)).isFalse();
    }
}
