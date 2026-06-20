package com.eczam.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @Size(max = 100) String displayName) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record PasswordResetRequest(@Email @NotBlank String email) {}

    public record PasswordResetConfirm(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 100) String newPassword) {}

    public record UserSummary(String id, String email, String displayName) {}

    public record AuthResponse(UserSummary user, String accessToken, String refreshToken) {}

    private AuthDtos() {}
}
