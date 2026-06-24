package com.eczam.users;

import com.eczam.shared.security.CurrentUser;
import com.eczam.shared.web.ApiResponse;
import com.eczam.users.dto.UserDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Users", description = "Authenticated user's profile and notification preferences")
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService users;

    public UserController(UserService users) { this.users = users; }

    @Operation(summary = "Get current user profile",
               description = "Returns the full profile of the authenticated user, including email verification status, linked auth methods, and notification preferences.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User profile")
    @GetMapping("/me")
    public ApiResponse<UserProfile> me(@CurrentUser UUID userId) {
        return ApiResponse.ok(users.getProfile(userId));
    }

    @Operation(summary = "Update display name",
               description = "Updates the authenticated user's display name.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated profile"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Validation failed", content = @Content)
    })
    @PatchMapping("/me")
    public ApiResponse<UserProfile> updateMe(@CurrentUser UUID userId,
                                             @Valid @RequestBody UpdateProfileRequest req) {
        return ApiResponse.ok(users.updateProfile(userId, req));
    }

    @Operation(summary = "Update notification preferences",
               description = "Toggles push/email notifications and sets low-stock and expiry warning thresholds.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Updated profile with new preferences"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Validation failed", content = @Content)
    })
    @PatchMapping("/me/preferences")
    public ApiResponse<UserProfile> updatePrefs(@CurrentUser UUID userId,
                                                @Valid @RequestBody UpdatePreferencesRequest req) {
        return ApiResponse.ok(users.updatePreferences(userId, req));
    }

    @Operation(summary = "Change email address",
               description = "Updates the account email. Requires current password to confirm. Resets email verification status and sends a new verification email. All sessions are revoked.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Email updated, verification email sent"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Password incorrect", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "New email already in use", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Invalid email format", content = @Content)
    })
    @PostMapping("/me/change-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeEmail(@CurrentUser UUID userId,
                            @Valid @RequestBody ChangeEmailRequest req,
                            HttpServletRequest request) {
        users.changeEmail(userId, req, request);
    }
}
