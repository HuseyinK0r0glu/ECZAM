package com.eczam.logs.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class LogDtos {

    public record CreateLogRequest(
            @NotNull String userMedicationId,
            @NotNull @Positive BigDecimal quantityUsed,
            String scheduleId,
            String notes) {}

    public record LogView(String id, String userMedicationId, String scheduleId,
                          OffsetDateTime takenAt, BigDecimal quantityUsed, String notes) {}

    public record LogResult(LogView log, BigDecimal newQuantity, boolean lowStock) {}

    private LogDtos() {}
}
