package com.eczam.logs.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class LogDtos {

    public record CreateLogRequest(
            @NotNull String userMedicationId,
            @NotNull @Positive BigDecimal quantityUsed,
            String scheduleId,
            String notes,
            // Optional idempotency key (≤64 chars). The offline client derives a
            // stable value per (box, reminder-time, day) so a retried/queued POST
            // never decrements stock twice.
            @jakarta.validation.constraints.Size(max = 64) String clientRequestId) {}

    public record LogView(String id, String userMedicationId, String scheduleId,
                          OffsetDateTime takenAt, BigDecimal quantityUsed, String notes) {}

    public record LogResult(LogView log, BigDecimal newQuantity, boolean lowStock) {}

    /** One day's expected-vs-taken dose count (see {@code AdherenceService} for the rule). */
    public record DayAdherence(String date, int expected, int taken) {}

    /** `GET /medication-logs/adherence` response — see {@code AdherenceService}. */
    public record AdherenceSummary(int currentStreak, int longestStreak, int windowDays,
                                   List<DayAdherence> days) {}

    private LogDtos() {}
}
