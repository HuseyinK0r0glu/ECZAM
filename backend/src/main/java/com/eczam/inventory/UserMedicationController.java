package com.eczam.inventory;

import com.eczam.inventory.dto.InventoryDtos.*;
import com.eczam.shared.security.CurrentUser;
import com.eczam.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Inventory", description = "Personal medication cabinet — add medications to track, manage quantities, and set expiry dates")
@RestController
@RequestMapping("/user-medications")
public class UserMedicationController {

    private final UserMedicationService service;
    public UserMedicationController(UserMedicationService service) { this.service = service; }

    @Operation(summary = "List inventory",
               description = "Returns all medications in the authenticated user's cabinet with current quantities, expiry dates, and status flags (low stock, expiring soon, expired).")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Inventory list")
    @GetMapping
    public ApiResponse<List<InventoryItem>> list(@CurrentUser UUID userId) {
        return ApiResponse.ok(service.list(userId));
    }

    @Operation(summary = "Get inventory item",
               description = "Returns a single inventory entry by ID.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Inventory item"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Item not found or not owned by caller", content = @Content)
    })
    @GetMapping("/{id}")
    public ApiResponse<InventoryItem> get(@CurrentUser UUID userId, @PathVariable UUID id) {
        return ApiResponse.ok(service.get(userId, id));
    }

    @Operation(summary = "Add medication to inventory",
               description = "Creates a new entry in the user's cabinet linking a global medication to a personal quantity and optional expiry date.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Inventory item created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Medication not found in global catalog", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Validation failed", content = @Content)
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InventoryItem> create(@CurrentUser UUID userId,
                                             @Valid @RequestBody CreateInventoryRequest req) {
        return ApiResponse.ok(service.create(userId, req));
    }

    @Operation(summary = "Update inventory item",
               description = "Updates quantity, expiry date, or custom name. Use this for manual stock adjustments (not dose logging — use POST /medication-logs for that).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated inventory item"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Item not found or not owned by caller", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Validation failed", content = @Content)
    })
    @PatchMapping("/{id}")
    public ApiResponse<InventoryItem> update(@CurrentUser UUID userId, @PathVariable UUID id,
                                             @Valid @RequestBody UpdateInventoryRequest req) {
        return ApiResponse.ok(service.update(userId, id, req));
    }

    @Operation(summary = "Remove medication from inventory",
               description = "Deletes the inventory entry. Dose logs are retained.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Item not found or not owned by caller", content = @Content)
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
    }
}
