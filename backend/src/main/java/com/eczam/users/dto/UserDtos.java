package com.eczam.users.dto;

import com.eczam.users.NotificationPreferences;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    public record UserProfile(String id, String email, String displayName,
                              NotificationPreferences notificationPreferences) {}

    public record UpdateProfileRequest(@Size(max = 100) String displayName) {}

    public record UpdatePreferencesRequest(
            Boolean push, Boolean email,
            @Min(0) Integer lowStockThreshold,
            @Min(0) Integer expiryWarningDays) {}

    private UserDtos() {}
}
