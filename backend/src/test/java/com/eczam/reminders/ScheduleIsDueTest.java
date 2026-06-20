package com.eczam.reminders;

import com.eczam.inventory.UserMedication;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleIsDueTest {

    private MedicationSchedule base(FrequencyType type) {
        MedicationSchedule s = new MedicationSchedule();
        s.setUserMedication(new UserMedication());
        s.setActive(true);
        s.setDosageAmount(BigDecimal.ONE);
        s.setFrequencyType(type);
        s.setScheduledTimes(new LocalTime[]{ LocalTime.of(8, 0), LocalTime.of(20, 0) });
        s.setStartsOn(LocalDate.of(2026, 1, 1));
        return s;
    }

    @Test void daily_due_at_scheduled_minute() {
        var s = base(FrequencyType.daily);
        assertThat(ScheduleService.isDue(s, LocalDateTime.of(2026, 6, 18, 8, 0))).isTrue();
        assertThat(ScheduleService.isDue(s, LocalDateTime.of(2026, 6, 18, 9, 0))).isFalse();
    }

    @Test void paused_is_never_due() {
        var s = base(FrequencyType.daily); s.setActive(false);
        assertThat(ScheduleService.isDue(s, LocalDateTime.of(2026, 6, 18, 8, 0))).isFalse();
    }

    @Test void weekly_only_on_listed_days() {
        var s = base(FrequencyType.weekly);
        s.setDaysOfWeek(new Short[]{ 1, 3, 5 }); // Mon/Wed/Fri
        assertThat(ScheduleService.isDue(s, LocalDateTime.of(2026, 6, 17, 8, 0))).isTrue();  // Wed
        assertThat(ScheduleService.isDue(s, LocalDateTime.of(2026, 6, 18, 8, 0))).isFalse(); // Thu
    }

    @Test void interval_every_n_days_from_start() {
        var s = base(FrequencyType.interval); s.setFrequencyValue(2);
        s.setStartsOn(LocalDate.of(2026, 6, 16));
        assertThat(ScheduleService.isDue(s, LocalDateTime.of(2026, 6, 16, 8, 0))).isTrue();
        assertThat(ScheduleService.isDue(s, LocalDateTime.of(2026, 6, 17, 8, 0))).isFalse();
        assertThat(ScheduleService.isDue(s, LocalDateTime.of(2026, 6, 18, 8, 0))).isTrue();
    }

    @Test void before_start_or_after_end_is_not_due() {
        var s = base(FrequencyType.daily);
        s.setStartsOn(LocalDate.of(2026, 6, 18));
        s.setEndsOn(LocalDate.of(2026, 6, 20));
        assertThat(ScheduleService.isDue(s, LocalDateTime.of(2026, 6, 17, 8, 0))).isFalse(); // before start
        assertThat(ScheduleService.isDue(s, LocalDateTime.of(2026, 6, 21, 8, 0))).isFalse(); // after end
        assertThat(ScheduleService.isDue(s, LocalDateTime.of(2026, 6, 19, 8, 0))).isTrue();  // within range
    }
}
