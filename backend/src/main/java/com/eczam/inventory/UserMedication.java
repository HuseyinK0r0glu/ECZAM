package com.eczam.inventory;

import com.eczam.medications.Medication;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_medications",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "medication_id", "expiration_date"}))
@Getter @Setter @NoArgsConstructor
public class UserMedication {

    @Id @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @Column(nullable = false)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(nullable = false)
    private String unit = "pill";

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(columnDefinition = "text")
    private String notes;

    @CreationTimestamp @Column(name = "added_at", updatable = false)
    private OffsetDateTime addedAt;

    @UpdateTimestamp @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
