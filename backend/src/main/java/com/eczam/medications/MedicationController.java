package com.eczam.medications;

import com.eczam.medications.dto.MedicationDtos.*;
import com.eczam.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Medications", description = "Global medication catalog — search, barcode lookup, and drug leaflet access")
@RestController
@RequestMapping("/medications")
public class MedicationController {

    private final MedicationService service;
    public MedicationController(MedicationService service) { this.service = service; }

    @Operation(summary = "Search medication catalog",
               description = "Full-text search across medication names. Returns up to `limit` results with cursor-based pagination metadata.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Medication list with cursor meta")
    @GetMapping
    public ApiResponse<List<MedicationView>> list(
            @Parameter(description = "Search query (name, brand, or active ingredient)") @RequestParam(required = false) String q,
            @Parameter(description = "Max results (default 20, max 100)") @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(service.search(q, limit), service.cursorMeta(limit));
    }

    @Operation(summary = "Get medication details",
               description = "Returns full details including leaflet text and any associated vector embeddings.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Medication found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Medication not found", content = @Content)
    })
    @GetMapping("/{id}")
    public ApiResponse<MedicationDetail> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "Create a medication",
               description = "Adds a new medication to the global catalog. Typically called by admin or during barcode/OpenFDA import.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Medication created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Validation failed", content = @Content)
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MedicationDetail> create(@Valid @RequestBody CreateMedicationRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @Operation(summary = "Look up by barcode",
               description = "Looks up a medication by its barcode (EAN-13, UPC, or DataMatrix). Returns the matched medication or 404.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Medication found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Barcode not found", content = @Content)
    })
    @GetMapping("/barcode/{code}")
    public ApiResponse<MedicationDetail> byBarcode(
            @Parameter(description = "Barcode string") @PathVariable String code) {
        return ApiResponse.ok(service.lookupByBarcode(code));
    }

    @Operation(summary = "Get medication leaflet",
               description = "Returns the full text of the official drug leaflet (package insert). Same as GET /medications/{id} but makes intent explicit for frontend routing.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Medication with leaflet text")
    @GetMapping("/{id}/leaflet")
    public ApiResponse<MedicationDetail> leaflet(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @Operation(summary = "Search within a medication's leaflet",
               description = "Semantic search over this medication's leaflet chunks using vector similarity. Returns the most relevant passages for the query.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Relevant leaflet passages"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Medication not found", content = @Content)
    })
    @GetMapping("/{id}/leaflet/search")
    public ApiResponse<LeafletSearchResult> searchLeaflet(
            @PathVariable UUID id,
            @Parameter(description = "Natural-language search query") @RequestParam String q) {
        return ApiResponse.ok(service.searchLeaflet(id, q));
    }
}
