package com.eczam.inventory;

import com.eczam.inventory.dto.InventoryDtos.*;
import com.eczam.shared.security.CurrentUser;
import com.eczam.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/user-medications")
public class UserMedicationController {

    private final UserMedicationService service;
    public UserMedicationController(UserMedicationService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<InventoryItem>> list(@CurrentUser UUID userId) {
        return ApiResponse.ok(service.list(userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<InventoryItem> get(@CurrentUser UUID userId, @PathVariable UUID id) {
        return ApiResponse.ok(service.get(userId, id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InventoryItem> create(@CurrentUser UUID userId,
                                             @Valid @RequestBody CreateInventoryRequest req) {
        return ApiResponse.ok(service.create(userId, req));
    }

    @PatchMapping("/{id}")
    public ApiResponse<InventoryItem> update(@CurrentUser UUID userId, @PathVariable UUID id,
                                             @Valid @RequestBody UpdateInventoryRequest req) {
        return ApiResponse.ok(service.update(userId, id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
    }
}
