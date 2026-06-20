package com.eczam.logs;

import com.eczam.logs.dto.LogDtos.*;
import com.eczam.shared.security.CurrentUser;
import com.eczam.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/medication-logs")
public class MedicationLogController {

    private final MedicationLogService service;
    public MedicationLogController(MedicationLogService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<LogResult> log(@CurrentUser UUID userId, @Valid @RequestBody CreateLogRequest req) {
        return ApiResponse.ok(service.logDose(userId, req));
    }

    @GetMapping
    public ApiResponse<List<LogView>> history(
            @CurrentUser UUID userId,
            @RequestParam UUID userMedicationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(service.history(userId, userMedicationId, from, to, limit));
    }
}
