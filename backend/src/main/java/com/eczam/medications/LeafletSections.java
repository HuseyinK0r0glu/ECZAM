package com.eczam.medications;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Structured leaflet content stored as JSONB (brief §6.2). */
public record LeafletSections(
        String dosage,
        @JsonProperty("side_effects") String sideEffects,
        String contraindications,
        String storage,
        String interactions,
        @JsonProperty("missed_dose") String missedDose) {}
