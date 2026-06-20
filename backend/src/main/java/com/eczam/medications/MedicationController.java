package com.eczam.medications;

import com.eczam.medications.dto.MedicationDtos.*;
import com.eczam.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/medications")
public class MedicationController {

    private final MedicationService service;
    public MedicationController(MedicationService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<MedicationView>> list(@RequestParam(required = false) String q,
                                                  @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(service.search(q, limit), service.cursorMeta(limit));
    }

    @GetMapping("/{id}")
    public ApiResponse<MedicationDetail> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MedicationDetail> create(@Valid @RequestBody CreateMedicationRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @GetMapping("/barcode/{code}")
    public ApiResponse<MedicationDetail> byBarcode(@PathVariable String code) {
        return ApiResponse.ok(service.lookupByBarcode(code));
    }

    @GetMapping("/{id}/leaflet")
    public ApiResponse<MedicationDetail> leaflet(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @GetMapping("/{id}/leaflet/search")
    public ApiResponse<LeafletSearchResult> searchLeaflet(@PathVariable UUID id, @RequestParam String q) {
        return ApiResponse.ok(service.searchLeaflet(id, q));
    }
}
