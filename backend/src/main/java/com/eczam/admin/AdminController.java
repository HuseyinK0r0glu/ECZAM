package com.eczam.admin;

import com.eczam.audit.AuditLog;
import com.eczam.shared.security.CurrentUser;
import com.eczam.shared.web.ApiResponse;
import com.eczam.users.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Tag(name = "Admin", description = "Admin-only endpoints — require ROLE_ADMIN. Protected by both SecurityConfig path rule and @PreAuthorize.")
@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService admin;

    public AdminController(AdminService admin) { this.admin = admin; }

    // ── Users ────────────────────────────────────────────────────────────────

    @Operation(summary = "List all users (paginated)",
               description = "Returns active (non-deleted) users. Optionally filter by `search` (matches email or display name).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Paginated user list"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not an admin", content = @Content)
    })
    @GetMapping("/users")
    public ApiResponse<Page<User>> listUsers(
            @Parameter(description = "Filter by email or display name (case-insensitive contains)") @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(admin.listUsers(search, page, size));
    }

    @Operation(summary = "Get a user by ID")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    @GetMapping("/users/{userId}")
    public ApiResponse<User> getUser(@PathVariable UUID userId) {
        return ApiResponse.ok(admin.getUser(userId));
    }

    @Operation(summary = "Lock a user account",
               description = "Prevents the user from logging in for the specified duration.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Account locked"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    @PostMapping("/users/{userId}/lock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void lockUser(
            @PathVariable UUID userId,
            @Parameter(description = "Lock duration in minutes (default 60)") @RequestParam(defaultValue = "60") int minutes,
            @CurrentUser UUID adminId,
            HttpServletRequest request) {
        admin.lockUser(userId, minutes, adminId, request);
    }

    @Operation(summary = "Unlock a user account",
               description = "Clears the lockout, allowing the user to log in again immediately.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Account unlocked"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    @PostMapping("/users/{userId}/unlock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlockUser(@PathVariable UUID userId,
                           @CurrentUser UUID adminId,
                           HttpServletRequest request) {
        admin.unlockUser(userId, adminId, request);
    }

    @Operation(summary = "Delete (anonymise) a user",
               description = "Anonymises the user's PII and sets `deletedAt`. Data is retained for audit integrity. All sessions are revoked.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "User anonymised"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    @DeleteMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable UUID userId,
                           @CurrentUser UUID adminId,
                           HttpServletRequest request) {
        admin.deleteUser(userId, adminId, request);
    }

    // ── Audit logs ───────────────────────────────────────────────────────────

    @Operation(summary = "Search audit logs",
               description = "Returns a paginated list of audit events. Filter by userId, eventType, and/or date range.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Paginated audit log entries")
    @GetMapping("/audit-logs")
    public ApiResponse<Page<AuditLog>> auditLogs(
            @Parameter(description = "Filter by user ID") @RequestParam(required = false) UUID userId,
            @Parameter(description = "Filter by event type (e.g. LOGIN_SUCCESS, REGISTER)") @RequestParam(required = false) String eventType,
            @Parameter(description = "From timestamp (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "To timestamp (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(admin.getAuditLogs(userId, eventType, from, to, page, size));
    }
}
