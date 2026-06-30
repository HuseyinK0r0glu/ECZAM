package com.eczam.logs;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Immutable record of a taken dose (brief §6.5). */
@Entity
@Table(name = "medication_logs")
@Getter @Setter @NoArgsConstructor
public class MedicationLog {

    @Id @GeneratedValue
    private UUID id;

    @Column(name = "user_medication_id", nullable = false)
    private UUID userMedicationId;

    @Column(name = "schedule_id")
    private UUID scheduleId;

    @Column(name = "taken_at", nullable = false)
    private OffsetDateTime takenAt = OffsetDateTime.now();

    @Column(name = "quantity_used", nullable = false)
    private BigDecimal quantityUsed;

    @Column(columnDefinition = "text")
    private String notes;

    /** Client-supplied idempotency key; replays with the same key are no-ops. */
    @Column(name = "client_request_id", length = 64)
    private String clientRequestId;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
