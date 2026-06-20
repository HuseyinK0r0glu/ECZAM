package com.eczam.medications;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "medications")
@Getter @Setter @NoArgsConstructor
public class Medication {

    @Id @GeneratedValue
    private UUID id;

    @Column(nullable = false) private String name;
    @Column(name = "generic_name") private String genericName;
    private String manufacturer;
    @Column(unique = true) private String barcode;
    private String form;
    private String strength;

    @Column(name = "leaflet_raw", columnDefinition = "text")
    private String leafletRaw;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "leaflet_sections", columnDefinition = "jsonb")
    private LeafletSections leafletSections;

    @Column(name = "vector_indexed", nullable = false)
    private boolean vectorIndexed = false;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
