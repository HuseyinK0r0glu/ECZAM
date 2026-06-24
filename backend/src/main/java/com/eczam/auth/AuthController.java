package com.eczam.auth;

import com.eczam.auth.dto.AuthDtos.*;
import com.eczam.shared.security.CurrentUser;
import com.eczam.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Auth", description = "Registration, login, token management, password/email operations, and account deletion")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) { this.auth = auth; }

    // ── Registration & Login ─────────────────────────────────────────────────

    @Operation(summary = "Register a new account",
               description = "Creates a user and returns access + refresh tokens. Sends an email verification link if SMTP is configured. Password must be ≥8 chars with uppercase, lowercase, digit, and special character.",
               security = {})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Account created — returns access + refresh tokens"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already in use", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Validation failed (weak password, invalid email …)", content = @Content)
    })
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest req, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(auth.register(req, request)));
    }

    @Operation(summary = "Log in with email + password",
               description = "Returns access + refresh tokens. After 5 failed attempts the account is locked for 15 minutes.",
               security = {})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful — returns access + refresh tokens"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Account temporarily locked after too many failures", content = @Content)
    })
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(
            @Valid @RequestBody LoginRequest req, HttpServletRequest request) {
        return ApiResponse.ok(auth.login(req, request));
    }

    // ── Google OAuth ─────────────────────────────────────────────────────────

    @Operation(summary = "Sign in / sign up with Google",
               description = "Verify a Google ID token obtained by the frontend from Google's Sign-In SDK. Creates an account on first use; links the Google sub to an existing account if the email already exists.",
               security = {})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Authentication successful"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid or expired Google ID token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already linked to a different Google account", content = @Content)
    })
    @PostMapping("/google")
    public ApiResponse<AuthResponse> googleLogin(
            @Valid @RequestBody GoogleLoginRequest req, HttpServletRequest request) {
        return ApiResponse.ok(auth.googleLogin(req.idToken(), request));
    }

    // ── Token management ─────────────────────────────────────────────────────

    @Operation(summary = "Rotate refresh token",
               description = "Exchange a valid refresh token for a new access + refresh token pair. The old token is immediately revoked. Re-using an already-revoked token triggers family-wide revocation (compromise protection).",
               security = {})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "New token pair issued"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Token invalid, expired, or already used", content = @Content)
    })
    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(
            @Valid @RequestBody RefreshRequest req, HttpServletRequest request) {
        return ApiResponse.ok(auth.refresh(req.refreshToken(), request));
    }

    @Operation(summary = "Log out — revoke one device",
               description = "Revokes the provided refresh token. The access token remains valid until its 2-hour TTL expires (stateless JWT), so clients should discard it immediately.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Logged out"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    })
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest req,
                       @CurrentUser UUID userId,
                       HttpServletRequest request) {
        auth.logout(req.refreshToken(), userId, request);
    }

    @Operation(summary = "Log out from all devices",
               description = "Revokes every active refresh token for the authenticated user.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "All sessions revoked"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    })
    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutAll(@CurrentUser UUID userId, HttpServletRequest request) {
        auth.logoutAll(userId, request);
    }

    // ── Session management ───────────────────────────────────────────────────

    @Operation(summary = "List active sessions",
               description = "Returns all non-expired, non-revoked refresh tokens for the authenticated user, with device info.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Session list")
    @GetMapping("/sessions")
    public ApiResponse<List<SessionView>> sessions(@CurrentUser UUID userId) {
        return ApiResponse.ok(auth.activeSessions(userId));
    }

    @Operation(summary = "Revoke a specific session",
               description = "Revokes the refresh token identified by sessionId. Users can only revoke their own sessions.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Session revoked"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Session not found or not owned by caller", content = @Content)
    })
    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(
            @Parameter(description = "Session ID from GET /auth/sessions") @PathVariable UUID sessionId,
            @CurrentUser UUID userId,
            HttpServletRequest request) {
        auth.revokeSession(sessionId, userId, request);
    }

    // ── Password management ──────────────────────────────────────────────────

    @Operation(summary = "Request password reset email",
               description = "Sends a reset link to the address if it exists. Always returns 204 to prevent email enumeration.",
               security = {})
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Email sent (or silently ignored if address unknown)")
    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestReset(@Valid @RequestBody PasswordResetRequest req,
                             HttpServletRequest request) {
        auth.requestReset(req.email(), request);
    }

    @Operation(summary = "Complete password reset",
               description = "Exchanges the one-time token from the reset email for a new password. Token expires in 30 minutes.",
               security = {})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Password changed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Token invalid or expired", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "New password fails policy", content = @Content)
    })
    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmReset(@Valid @RequestBody PasswordResetConfirm req,
                             HttpServletRequest request) {
        auth.confirmReset(req, request);
    }

    @Operation(summary = "Change password (while authenticated)",
               description = "Requires the current password. Revokes all refresh tokens after success — forces re-login on all devices.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Password changed, all sessions revoked"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Current password incorrect", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "New password fails policy", content = @Content)
    })
    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req,
                               @CurrentUser UUID userId,
                               HttpServletRequest request) {
        auth.changePassword(req, userId, request);
    }

    // ── Email verification ───────────────────────────────────────────────────

    @Operation(summary = "Verify email address",
               description = "Marks the account email as verified using the one-time token from the verification email. Token expires in 24 hours.",
               security = {})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Email verified"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Token invalid or expired", content = @Content)
    })
    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody VerifyEmailRequest req,
                            HttpServletRequest request) {
        auth.verifyEmail(req.token(), request);
    }

    @Operation(summary = "Resend verification email",
               description = "Sends a new verification email to the authenticated user's current address. Rate-limited to 5 calls per 15 minutes.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Verification email sent"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content)
    })
    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification(@CurrentUser UUID userId,
                                   HttpServletRequest request) {
        auth.resendVerification(userId, request);
    }

    // ── Account deletion ─────────────────────────────────────────────────────

    @Operation(summary = "Delete account",
               description = "Anonymises PII (email → placeholder, display name cleared) and sets deletedAt. All refresh tokens are revoked. For password accounts pass `password` as a query param to confirm.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Account deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated or password incorrect", content = @Content)
    })
    @DeleteMapping("/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(
            @Parameter(description = "Current password — required for password accounts, omit for Google-only accounts")
            @RequestParam(required = false) String password,
            @CurrentUser UUID userId,
            HttpServletRequest request) {
        auth.deleteAccount(userId, password, request);
    }
}
