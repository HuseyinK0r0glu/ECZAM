package com.eczam.reminders;

import com.eczam.reminders.dto.ScheduleDtos.*;
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

@Tag(name = "Schedules", description = "Medication reminder schedules — define when doses should be taken and receive push/email notifications")
@RestController
public class ScheduleController {

    private final ScheduleService service;
    public ScheduleController(ScheduleService service) { this.service = service; }

    @Operation(summary = "List all schedules",
               description = "Returns every active and paused schedule across all medications for the authenticated user.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "All schedules")
    @GetMapping("/schedules")
    public ApiResponse<List<ScheduleView>> all(@CurrentUser UUID userId) {
        return ApiResponse.ok(service.listForUser(userId));
    }

    @Operation(summary = "List schedules for a medication",
               description = "Returns schedules attached to a specific inventory item.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Schedules for this inventory item"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Inventory item not found or not owned by caller", content = @Content)
    })
    @GetMapping("/user-medications/{umId}/schedules")
    public ApiResponse<List<ScheduleView>> forMed(@CurrentUser UUID userId, @PathVariable UUID umId) {
        return ApiResponse.ok(service.listForUserMedication(userId, umId));
    }

    @Operation(summary = "Create a schedule",
               description = "Creates a new reminder schedule for the given inventory item. Specify time(s) of day, days of week, and dose amount.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Schedule created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Inventory item not found", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Validation failed", content = @Content)
    })
    @PostMapping("/user-medications/{umId}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ScheduleView> create(@CurrentUser UUID userId, @PathVariable UUID umId,
                                            @Valid @RequestBody CreateScheduleRequest req) {
        return ApiResponse.ok(service.create(userId, umId, req));
    }

    @Operation(summary = "Update a schedule",
               description = "Updates times, days, or dose amount for an existing schedule.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated schedule"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Schedule not found or not owned by caller", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Validation failed", content = @Content)
    })
    @PatchMapping("/schedules/{id}")
    public ApiResponse<ScheduleView> update(@CurrentUser UUID userId, @PathVariable UUID id,
                                            @Valid @RequestBody UpdateScheduleRequest req) {
        return ApiResponse.ok(service.update(userId, id, req));
    }

    @Operation(summary = "Pause a schedule",
               description = "Suspends reminders without deleting the schedule. Resume later with POST /schedules/{id}/resume.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Schedule paused"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Schedule not found or not owned by caller", content = @Content)
    })
    @PostMapping("/schedules/{id}/pause")
    public ApiResponse<ScheduleView> pause(@CurrentUser UUID userId, @PathVariable UUID id) {
        return ApiResponse.ok(service.setActive(userId, id, false));
    }

    @Operation(summary = "Resume a paused schedule",
               description = "Re-activates a paused schedule so reminders fire again from the next scheduled time.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Schedule resumed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Schedule not found or not owned by caller", content = @Content)
    })
    @PostMapping("/schedules/{id}/resume")
    public ApiResponse<ScheduleView> resume(@CurrentUser UUID userId, @PathVariable UUID id) {
        return ApiResponse.ok(service.setActive(userId, id, true));
    }

    @Operation(summary = "Delete a schedule",
               description = "Permanently removes the schedule and its pending reminders.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Schedule not found or not owned by caller", content = @Content)
    })
    @DeleteMapping("/schedules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
    }
}
