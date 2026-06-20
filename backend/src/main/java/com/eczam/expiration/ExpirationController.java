package com.eczam.expiration;

import com.eczam.inventory.UserMedicationRepository;
import com.eczam.inventory.UserMedicationService;
import com.eczam.inventory.dto.InventoryDtos.InventoryItem;
import com.eczam.shared.security.CurrentUser;
import com.eczam.shared.web.ApiResponse;
import com.eczam.users.UserRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/expiration")
public class ExpirationController {

    private final UserMedicationRepository repo;
    private final UserRepository users;

    public ExpirationController(UserMedicationRepository repo, UserRepository users) {
        this.repo = repo; this.users = users;
    }

    @GetMapping("/expiring-soon")
    public ApiResponse<List<InventoryItem>> expiringSoon(@CurrentUser UUID userId,
                                                         @RequestParam(required = false) Integer days) {
        int warn = users.findById(userId).map(u -> u.getNotificationPreferences().expiryWarningDays()).orElse(30);
        int low = users.findById(userId).map(u -> u.getNotificationPreferences().lowStockThreshold()).orElse(7);
        return ApiResponse.ok(repo.findExpiringSoonForUser(userId, days).stream()
                .map(um -> UserMedicationService.toItem(um, low, warn)).toList());
    }

    @GetMapping("/expired")
    public ApiResponse<List<InventoryItem>> expired(@CurrentUser UUID userId) {
        int warn = users.findById(userId).map(u -> u.getNotificationPreferences().expiryWarningDays()).orElse(30);
        int low = users.findById(userId).map(u -> u.getNotificationPreferences().lowStockThreshold()).orElse(7);
        return ApiResponse.ok(repo.findExpiredForUser(userId).stream()
                .map(um -> UserMedicationService.toItem(um, low, warn)).toList());
    }
}
