package com.eczam.reminders.dto;

import com.eczam.reminders.FrequencyType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ScheduleDtos {

    public record ScheduleView(
            String id, String userMedicationId, String medicationName,
            BigDecimal dosageAmount, FrequencyType frequencyType, Integer frequencyValue,
            List<String> scheduledTimes, List<Integer> daysOfWeek,
            boolean active, LocalDate startsOn, LocalDate endsOn) {}

    public record CreateScheduleRequest(
            @NotNull @Positive BigDecimal dosageAmount,
            @NotNull FrequencyType frequencyType,
            Integer frequencyValue,
            @NotEmpty List<String> scheduledTimes,   // ["08:00","20:00"]
            List<Integer> daysOfWeek,                 // [1,3,5] for weekly
            LocalDate startsOn,
            LocalDate endsOn) {}

    public record UpdateScheduleRequest(
            BigDecimal dosageAmount, FrequencyType frequencyType, Integer frequencyValue,
            List<String> scheduledTimes, List<Integer> daysOfWeek,
            LocalDate startsOn, LocalDate endsOn) {}

    private ScheduleDtos() {}
}
