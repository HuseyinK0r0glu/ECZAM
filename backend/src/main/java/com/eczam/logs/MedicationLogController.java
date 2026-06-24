package com.eczam.logs;

import com.eczam.logs.dto.LogDtos.*;
import com.eczam.shared.security.CurrentUser;
import com.eczam.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Tag(name = "Dose Logs", description = "Immutable dose log — record taken doses and view history. Each logged dose decrements the inventory quantity.")
@RestController
@RequestMapping("/medication-logs")
public class MedicationLogController {

    private final MedicationLogService service;
    public MedicationLogController(MedicationLogService service) { this.service = service; }

    @Operation(summary = "Log a dose",
               description = "Records that a dose was taken. Atomically decrements `user_medications.quantity` by `quantityUsed`. " +
                             "Returns 422 INSUFFICIENT_STOCK if there isn't enough remaining quantity. Logs are immutable once created.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Dose logged — returns new quantity"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Inventory item not found or not owned by caller", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Insufficient stock or validation failed", content = @Content)
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LogResult> log(@CurrentUser UUID userId,
                                      @Valid @RequestBody CreateLogRequest req) {
        return ApiResponse.ok(service.logDose(userId, req));
    }

    @Operation(summary = "Get dose history",
               description = "Returns dose log entries for a specific inventory item, newest first. Optionally filter by date range.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Dose history"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Inventory item not found or not owned by caller", content = @Content)
    })
    @GetMapping
    public ApiResponse<List<LogView>> history(
            @CurrentUser UUID userId,
            @Parameter(description = "ID of the inventory item (user_medications.id)") @RequestParam UUID userMedicationId,
            @Parameter(description = "Start of date range (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "End of date range (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @Parameter(description = "Max entries to return (default 50)") @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.history(userId, userMedicationId, from, to, limit));
    }
}
