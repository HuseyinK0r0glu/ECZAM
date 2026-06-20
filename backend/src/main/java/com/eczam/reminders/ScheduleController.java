package com.eczam.reminders;

import com.eczam.reminders.dto.ScheduleDtos.*;
import com.eczam.shared.security.CurrentUser;
import com.eczam.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class ScheduleController {

    private final ScheduleService service;
    public ScheduleController(ScheduleService service) { this.service = service; }

    @GetMapping("/schedules")
    public ApiResponse<List<ScheduleView>> all(@CurrentUser UUID userId) {
        return ApiResponse.ok(service.listForUser(userId));
    }

    @GetMapping("/user-medications/{umId}/schedules")
    public ApiResponse<List<ScheduleView>> forMed(@CurrentUser UUID userId, @PathVariable UUID umId) {
        return ApiResponse.ok(service.listForUserMedication(userId, umId));
    }

    @PostMapping("/user-medications/{umId}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ScheduleView> create(@CurrentUser UUID userId, @PathVariable UUID umId,
                                            @Valid @RequestBody CreateScheduleRequest req) {
        return ApiResponse.ok(service.create(userId, umId, req));
    }

    @PatchMapping("/schedules/{id}")
    public ApiResponse<ScheduleView> update(@CurrentUser UUID userId, @PathVariable UUID id,
                                            @Valid @RequestBody UpdateScheduleRequest req) {
        return ApiResponse.ok(service.update(userId, id, req));
    }

    @PostMapping("/schedules/{id}/pause")
    public ApiResponse<ScheduleView> pause(@CurrentUser UUID userId, @PathVariable UUID id) {
        return ApiResponse.ok(service.setActive(userId, id, false));
    }

    @PostMapping("/schedules/{id}/resume")
    public ApiResponse<ScheduleView> resume(@CurrentUser UUID userId, @PathVariable UUID id) {
        return ApiResponse.ok(service.setActive(userId, id, true));
    }

    @DeleteMapping("/schedules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser UUID userId, @PathVariable UUID id) {
        service.delete(userId, id);
    }
}
