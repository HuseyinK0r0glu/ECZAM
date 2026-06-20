package com.eczam.reminders;

import com.eczam.inventory.UserMedication;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "medication_schedules")
@Getter @Setter @NoArgsConstructor
public class MedicationSchedule {

    @Id @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_medication_id", nullable = false)
    private UserMedication userMedication;

    @Column(name = "dosage_amount", nullable = false)
    private BigDecimal dosageAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency_type", nullable = false)
    private FrequencyType frequencyType;

    @Column(name = "frequency_value")
    private Integer frequencyValue;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "scheduled_times", columnDefinition = "time[]", nullable = false)
    private LocalTime[] scheduledTimes;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "days_of_week", columnDefinition = "smallint[]")
    private Short[] daysOfWeek;   // ISO: 1=Mon … 7=Sun

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn = LocalDate.now();

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
