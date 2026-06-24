package com.eczam.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class AuthDtos {

    // ---- Requests ----

    @Schema(description = "New account registration")
    public record RegisterRequest(
            @Schema(description = "Account email address", example = "user@example.com")
            @Email @NotBlank String email,
            @Schema(description = "Password (8-100 chars, requires uppercase, lowercase, digit, and special character)", example = "SecureP@ss1")
            @NotBlank @Size(min = 8, max = 100) String password,
            @Schema(description = "Optional display name", example = "John Doe")
            @Size(max = 100) String displayName) {}

    @Schema(description = "Email + password sign-in")
    public record LoginRequest(
            @Schema(example = "user@example.com") @Email @NotBlank String email,
            @Schema(example = "SecureP@ss1") @NotBlank String password) {}

    @Schema(description = "Exchange a refresh token for new tokens")
    public record RefreshRequest(
            @Schema(description = "Opaque refresh token (returned by /auth/login or /auth/refresh)")
            @NotBlank String refreshToken) {}

    @Schema(description = "Revoke a specific session's refresh token")
    public record LogoutRequest(
            @Schema(description = "Refresh token of the session to revoke")
            @NotBlank String refreshToken) {}

    @Schema(description = "Request a password-reset email")
    public record PasswordResetRequest(
            @Schema(example = "user@example.com") @Email @NotBlank String email) {}

    @Schema(description = "Complete a password reset using the emailed token")
    public record PasswordResetConfirm(
            @Schema(description = "Reset token from the email link")
            @NotBlank String token,
            @Schema(description = "New password (8-100 chars, all policy rules apply)", example = "NewP@ss1!")
            @NotBlank @Size(min = 8, max = 100) String newPassword) {}

    @Schema(description = "Change password while authenticated")
    public record ChangePasswordRequest(
            @Schema(description = "Current password for verification") @NotBlank String currentPassword,
            @Schema(description = "New password (8-100 chars, all policy rules apply)", example = "NewP@ss1!")
            @NotBlank @Size(min = 8, max = 100) String newPassword) {}

    @Schema(description = "Sign in with Google ID token")
    public record GoogleLoginRequest(
            @Schema(description = "Google ID token obtained from Google Sign-In client library")
            @NotBlank String idToken) {}

    @Schema(description = "Verify email address with token from email link")
    public record VerifyEmailRequest(
            @Schema(description = "Email verification token from the verification link")
            @NotBlank String token) {}

    // ---- Responses ----

    @Schema(description = "Compact user summary embedded in auth responses")
    public record UserSummary(
            @Schema(description = "User UUID", example = "550e8400-e29b-41d4-a716-446655440000")
            String id,
            @Schema(example = "user@example.com") String email,
            @Schema(example = "John Doe") String displayName,
            @Schema(description = "Whether the email address has been verified") boolean emailVerified,
            @Schema(description = "Role — USER or ADMIN", example = "USER") String role) {}

    @Schema(description = "Successful authentication response")
    public record AuthResponse(
            UserSummary user,
            @Schema(description = "Short-lived JWT access token (Bearer) — include in Authorization header")
            String accessToken,
            @Schema(description = "Opaque refresh token — store securely (HttpOnly cookie recommended)")
            String refreshToken) {}

    @Schema(description = "Active session details")
    public record SessionView(
            @Schema(description = "Session/refresh-token ID") UUID id,
            @Schema(description = "User-Agent of the device that created this session") String userAgent,
            @Schema(description = "IP address the session was created from") String ipAddress,
            OffsetDateTime createdAt,
            OffsetDateTime expiresAt) {}

    private AuthDtos() {}
}
