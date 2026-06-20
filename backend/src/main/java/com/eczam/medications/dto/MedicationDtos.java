package com.eczam.medications.dto;

import com.eczam.medications.LeafletSections;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class MedicationDtos {

    public record MedicationView(
            String id, String name, String genericName, String manufacturer,
            String barcode, String form, String strength,
            boolean vectorIndexed) {}

    public record MedicationDetail(
            String id, String name, String genericName, String manufacturer,
            String barcode, String form, String strength,
            LeafletSections leafletSections, boolean vectorIndexed) {}

    public record CreateMedicationRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 255) String genericName,
            @Size(max = 255) String manufacturer,
            @Size(max = 100) String barcode,
            @Size(max = 50) String form,
            @Size(max = 50) String strength,
            String leafletRaw,
            LeafletSections leafletSections) {}

    public record LeafletSearchHit(String section, String snippet) {}

    public record LeafletSearchResult(List<LeafletSearchHit> hits) {}

    private MedicationDtos() {}
}
