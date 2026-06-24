package com.eczam.users.dto;

import com.eczam.users.NotificationPreferences;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public final class UserDtos {

    @Schema(description = "Full user profile including notification preferences")
    public record UserProfile(
            @Schema(description = "User UUID", example = "550e8400-e29b-41d4-a716-446655440000") String id,
            @Schema(example = "user@example.com") String email,
            @Schema(example = "John Doe") String displayName,
            @Schema(description = "Whether the email address has been verified") boolean emailVerified,
            @Schema(description = "Role — USER or ADMIN", example = "USER") String role,
            @Schema(description = "Whether a local password is set (false for Google-only accounts)") boolean hasPassword,
            @Schema(description = "Whether a Google account is linked") boolean hasGoogleLinked,
            NotificationPreferences notificationPreferences) {}

    @Schema(description = "Fields available for profile update")
    public record UpdateProfileRequest(
            @Schema(description = "New display name (null = no change)", example = "Jane Doe")
            @Size(max = 100) String displayName) {}

    @Schema(description = "Notification preference update — only provided fields are changed")
    public record UpdatePreferencesRequest(
            @Schema(description = "Enable/disable push notifications") Boolean push,
            @Schema(description = "Enable/disable email notifications") Boolean email,
            @Schema(description = "Low-stock warning threshold in pills/units (0 = disabled)", example = "7")
            @Min(0) Integer lowStockThreshold,
            @Schema(description = "Days before expiry to start showing warnings (0 = disabled)", example = "30")
            @Min(0) Integer expiryWarningDays) {}

    @Schema(description = "Change email address request — requires current password confirmation")
    public record ChangeEmailRequest(
            @Schema(example = "new@example.com") @Email @NotBlank String newEmail,
            @Schema(description = "Current password to confirm the change") @NotBlank String password) {}

    private UserDtos() {}
}
