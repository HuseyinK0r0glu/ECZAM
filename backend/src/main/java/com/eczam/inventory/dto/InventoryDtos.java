package com.eczam.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class InventoryDtos {

    public enum ExpiryStatus { OK, EXPIRING_SOON, EXPIRED }

    public record InventoryItem(
            String id, String medicationId, String medicationName, String strength, String form,
            BigDecimal quantity, String unit, LocalDate expirationDate, String notes,
            // Per-physical-box GS1 facts (AI 10 / AI 21), decoded fresh on each scan.
            String batch, String serialNumber,
            boolean lowStock, ExpiryStatus expiryStatus) {}

    public record CreateInventoryRequest(
            @NotNull String medicationId,
            @NotNull @DecimalMin("0.0") BigDecimal quantity,
            @Size(max = 20) String unit,
            LocalDate expirationDate,
            @Size(max = 64) String batch,
            @Size(max = 64) String serialNumber,
            String notes) {}

    public record UpdateInventoryRequest(
            @DecimalMin("0.0") BigDecimal quantity,
            @Size(max = 20) String unit,
            LocalDate expirationDate,
            @Size(max = 64) String batch,
            @Size(max = 64) String serialNumber,
            String notes) {}

    private InventoryDtos() {}
}
