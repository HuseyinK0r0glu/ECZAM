package com.eczam.expiration;

import com.eczam.inventory.UserMedicationRepository;
import com.eczam.inventory.UserMedicationService;
import com.eczam.inventory.dto.InventoryDtos.InventoryItem;
import com.eczam.shared.security.CurrentUser;
import com.eczam.shared.web.ApiResponse;
import com.eczam.users.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Expiration", description = "Proactive expiry monitoring — items nearing or past their expiration date")
@RestController
@RequestMapping("/expiration")
public class ExpirationController {

    private final UserMedicationRepository repo;
    private final UserRepository users;

    public ExpirationController(UserMedicationRepository repo, UserRepository users) {
        this.repo = repo; this.users = users;
    }

    @Operation(summary = "List medications expiring soon",
               description = "Returns inventory items whose expiration date falls within the warning window. " +
                             "The default window is the user's `expiryWarningDays` preference (default 30 days). " +
                             "Pass `days` to override for this request only.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Items expiring within the window")
    @GetMapping("/expiring-soon")
    public ApiResponse<List<InventoryItem>> expiringSoon(
            @CurrentUser UUID userId,
            @Parameter(description = "Override warning window in days (omit to use user preference, default 30)")
            @RequestParam(required = false) Integer days) {
        int warn = users.findById(userId).map(u -> u.getNotificationPreferences().expiryWarningDays()).orElse(30);
        int low  = users.findById(userId).map(u -> u.getNotificationPreferences().lowStockThreshold()).orElse(7);
        return ApiResponse.ok(repo.findExpiringSoonForUser(userId, days).stream()
                .map(um -> UserMedicationService.toItem(um, low, warn)).toList());
    }

    @Operation(summary = "List expired medications",
               description = "Returns inventory items whose expiration date is in the past. These should no longer be taken.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Expired items")
    @GetMapping("/expired")
    public ApiResponse<List<InventoryItem>> expired(@CurrentUser UUID userId) {
        int warn = users.findById(userId).map(u -> u.getNotificationPreferences().expiryWarningDays()).orElse(30);
        int low  = users.findById(userId).map(u -> u.getNotificationPreferences().lowStockThreshold()).orElse(7);
        return ApiResponse.ok(repo.findExpiredForUser(userId).stream()
                .map(um -> UserMedicationService.toItem(um, low, warn)).toList());
    }
}
