package com.eczam.medications;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
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

    /** Canonical 14-digit GTIN — the join key for GS1 DataMatrix scans (AI 01). */
    @Column(unique = true) private String gtin;

    @Column(name = "atc_code") private String atcCode;
    @Column(name = "atc_group") private String atcGroup;
    @Column(name = "active_ingredient") private String activeIngredient;

    /** Cleaned ordered therapeutic-category path (sentinels removed). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "category_path", columnDefinition = "jsonb")
    private List<String> categoryPath;

    private String form;
    private String strength;

    @Column(name = "leaflet_raw", columnDefinition = "text")
    private String leafletRaw;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "leaflet_sections", columnDefinition = "jsonb")
    private LeafletSections leafletSections;

    /** Source leaflet text was truncated at the ~32k scrape ceiling. */
    @Column(name = "leaflet_truncated", nullable = false)
    private boolean leafletTruncated = false;

    /** SHA-256 of leafletRaw; lets re-embed skip unchanged leaflets. */
    @Column(name = "leaflet_hash") private String leafletHash;

    @Column(name = "vector_indexed", nullable = false)
    private boolean vectorIndexed = false;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
