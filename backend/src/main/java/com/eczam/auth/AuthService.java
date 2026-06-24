package com.eczam.auth;

import com.eczam.audit.AuditEventType;
import com.eczam.audit.AuditService;
import com.eczam.auth.dto.AuthDtos.*;
import com.eczam.auth.email.EmailService;
import com.eczam.auth.oauth.GoogleOAuthService;
import com.eczam.auth.token.EmailVerificationToken;
import com.eczam.auth.token.EmailVerificationTokenRepository;
import com.eczam.auth.token.RefreshTokenService;
import com.eczam.shared.security.JwtService;
import com.eczam.shared.security.PasswordPolicy;
import com.eczam.shared.security.UserRole;
import com.eczam.shared.web.ApiException;
import com.eczam.shared.web.ErrorCode;
import com.eczam.users.User;
import com.eczam.users.UserRepository;
import io.micrometer.core.instrument.Counter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;
    private static final int VERIFICATION_TOKEN_HOURS = 24;

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;
    private final EmailVerificationTokenRepository verificationTokens;
    private final EmailService email;
    private final GoogleOAuthService googleOAuth;
    private final PasswordPolicy passwordPolicy;
    private final AuditService audit;
    private final Counter registrationsCounter;
    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;
    private final Counter googleLoginCounter;
    private final Counter accountLockedCounter;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt,
                       RefreshTokenService refreshTokens,
                       EmailVerificationTokenRepository verificationTokens,
                       EmailService email, GoogleOAuthService googleOAuth,
                       PasswordPolicy passwordPolicy, AuditService audit,
                       @Qualifier("userRegistrationsCounter") Counter registrationsCounter,
                       @Qualifier("loginSuccessCounter") Counter loginSuccessCounter,
                       @Qualifier("loginFailureCounter") Counter loginFailureCounter,
                       @Qualifier("googleLoginCounter") Counter googleLoginCounter,
                       @Qualifier("accountLockedCounter") Counter accountLockedCounter) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.verificationTokens = verificationTokens;
        this.email = email;
        this.googleOAuth = googleOAuth;
        this.passwordPolicy = passwordPolicy;
        this.audit = audit;
        this.registrationsCounter = registrationsCounter;
        this.loginSuccessCounter = loginSuccessCounter;
        this.loginFailureCounter = loginFailureCounter;
        this.googleLoginCounter = googleLoginCounter;
        this.accountLockedCounter = accountLockedCounter;
    }

    // =========================================================================
    // Registration
    // =========================================================================

    @Transactional
    public AuthResponse register(RegisterRequest req, HttpServletRequest request) {
        String normalEmail = req.email().toLowerCase().strip();
        if (users.existsByEmail(normalEmail)) {
            throw ApiException.conflict(ErrorCode.EMAIL_TAKEN, "Email already registered");
        }

        passwordPolicy.validate(req.password());

        User u = new User();
        u.setEmail(normalEmail);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setDisplayName(req.displayName() != null ? req.displayName().strip() : null);
        users.save(u);

        // Send verification email (async — never blocks registration)
        String verToken = issueVerificationToken(u);
        email.sendVerificationEmail(u.getEmail(), displayName(u), verToken);
        email.sendWelcomeEmail(u.getEmail(), displayName(u));

        registrationsCounter.increment();
        audit.log(AuditEventType.REGISTER, u.getId(), request,
                AuditService.details("email", u.getEmail()));

        return buildAuthResponse(u, request);
    }

    // =========================================================================
    // Login
    // =========================================================================

    // noRollbackFor: failed-login counter increments must persist even when we throw 401
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest req, HttpServletRequest request) {
        String normalEmail = req.email().toLowerCase().strip();
        User u = users.findActiveByEmail(normalEmail)
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));

        // Check lockout before wasting time on bcrypt
        if (u.isLocked()) {
            accountLockedCounter.increment();
            audit.log(AuditEventType.LOGIN_LOCKED, u.getId(), request);
            throw ApiException.locked("Account is temporarily locked due to too many failed attempts. " +
                    "Try again after " + u.getLockedUntil());
        }

        if (!u.hasPassword()) {
            throw ApiException.unauthorized("This account uses Google Sign-In. " +
                    "Please use 'Continue with Google' to log in.");
        }

        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            handleFailedLogin(u);
            loginFailureCounter.increment();
            audit.log(AuditEventType.LOGIN_FAILURE, u.getId(), request);
            throw ApiException.unauthorized("Invalid email or password");
        }

        // Success — reset lockout counter
        u.setFailedLoginAttempts(0);
        u.setLockedUntil(null);
        users.save(u);

        loginSuccessCounter.increment();
        audit.log(AuditEventType.LOGIN_SUCCESS, u.getId(), request,
                AuditService.details("email", u.getEmail()));

        return buildAuthResponse(u, request);
    }

    // =========================================================================
    // Google OAuth
    // =========================================================================

    @Transactional
    public AuthResponse googleLogin(String idToken, HttpServletRequest request) {
        var googleUser = googleOAuth.verify(idToken);

        // Try to find existing user by google_sub first, then by email
        User u = users.findByGoogleSub(googleUser.sub())
                .or(() -> users.findActiveByEmail(googleUser.email()))
                .orElse(null);

        if (u == null) {
            // New user via Google
            u = new User();
            u.setEmail(googleUser.email());
            // Google accounts have no password — set empty marker
            u.setPasswordHash(null);
            u.setDisplayName(googleUser.name());
            u.setGoogleSub(googleUser.sub());
            u.setEmailVerified(googleUser.emailVerified());
            users.save(u);
            audit.log(AuditEventType.REGISTER, u.getId(), request,
                    AuditService.details("via", "google", "email", u.getEmail()));
        } else {
            // Existing user — link Google if not already linked
            if (u.getGoogleSub() == null) {
                u.setGoogleSub(googleUser.sub());
                if (!u.isEmailVerified() && googleUser.emailVerified()) {
                    u.setEmailVerified(true);
                }
                users.save(u);
                audit.log(AuditEventType.GOOGLE_LINKED, u.getId(), request);
            }
        }

        if (u.isLocked()) {
            throw ApiException.locked("Account is temporarily locked");
        }

        googleLoginCounter.increment();
        audit.log(AuditEventType.GOOGLE_LOGIN, u.getId(), request);
        return buildAuthResponse(u, request);
    }

    // =========================================================================
    // Token refresh (rotation)
    // =========================================================================

    @Transactional
    public AuthResponse refresh(String rawRefreshToken, HttpServletRequest request) {
        var result = refreshTokens.rotate(rawRefreshToken, request);
        audit.log(AuditEventType.TOKEN_REFRESH, result.user().getId(), request);

        String accessToken = jwt.generateAccess(result.user().getId());
        return new AuthResponse(toSummary(result.user()), accessToken, result.rawToken());
    }

    // =========================================================================
    // Logout
    // =========================================================================

    @Transactional
    public void logout(String rawRefreshToken, UUID userId, HttpServletRequest request) {
        if (rawRefreshToken != null) {
            refreshTokens.revoke(rawRefreshToken);
        }
        audit.log(AuditEventType.LOGOUT, userId, request);
    }

    @Transactional
    public void logoutAll(UUID userId, HttpServletRequest request) {
        refreshTokens.revokeAll(userId);
        audit.log(AuditEventType.LOGOUT_ALL, userId, request);
    }

    // =========================================================================
    // Sessions
    // =========================================================================

    @Transactional(readOnly = true)
    public List<SessionView> activeSessions(UUID userId) {
        return refreshTokens.activeSessions(userId)
                .stream()
                .map(t -> new SessionView(t.getId(), t.getUserAgent(),
                        t.getIpAddress(), t.getCreatedAt(), t.getExpiresAt()))
                .toList();
    }

    @Transactional
    public void revokeSession(UUID tokenId, UUID userId, HttpServletRequest request) {
        refreshTokens.revokeSession(tokenId, userId);
        audit.log(AuditEventType.LOGOUT, userId, request,
                AuditService.details("sessionId", tokenId.toString()));
    }

    // =========================================================================
    // Password reset
    // =========================================================================

    @Transactional
    public void requestReset(String rawEmail, HttpServletRequest request) {
        String normalEmail = rawEmail.toLowerCase().strip();
        users.findActiveByEmail(normalEmail).ifPresent(u -> {
            String resetToken = jwt.generateReset(u.getId());
            email.sendPasswordResetEmail(u.getEmail(), displayName(u), resetToken);
            audit.log(AuditEventType.PASSWORD_RESET_REQUEST, u.getId(), request);
        });
        // Always succeeds (non-enumerating)
    }

    @Transactional
    public void confirmReset(PasswordResetConfirm req, HttpServletRequest request) {
        UUID userId;
        try {
            userId = jwt.verify(req.token(), JwtService.TokenType.RESET);
        } catch (Exception e) {
            throw ApiException.badRequest(ErrorCode.RESET_TOKEN_INVALID, "Invalid or expired reset token");
        }
        User u = users.findById(userId)
                .orElseThrow(() -> ApiException.badRequest(ErrorCode.RESET_TOKEN_INVALID, "Invalid reset token"));

        passwordPolicy.validate(req.newPassword());
        u.setPasswordHash(encoder.encode(req.newPassword()));
        u.setFailedLoginAttempts(0);
        u.setLockedUntil(null);
        users.save(u);

        // Invalidate all existing refresh tokens after password reset
        refreshTokens.revokeAll(u.getId());
        audit.log(AuditEventType.PASSWORD_RESET_CONFIRM, u.getId(), request);
    }

    // =========================================================================
    // Change password (authenticated)
    // =========================================================================

    @Transactional
    public void changePassword(ChangePasswordRequest req, UUID userId, HttpServletRequest request) {
        User u = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        if (!u.hasPassword()) {
            throw ApiException.badRequest(ErrorCode.CURRENT_PASSWORD_WRONG,
                    "Google-linked accounts don't have a password to change");
        }
        if (!encoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw ApiException.unauthorized("Current password is incorrect");
        }

        passwordPolicy.validate(req.newPassword());
        u.setPasswordHash(encoder.encode(req.newPassword()));
        users.save(u);

        // Revoke all sessions so other devices must re-authenticate
        refreshTokens.revokeAll(userId);
        audit.log(AuditEventType.PASSWORD_CHANGED, userId, request);
    }

    // =========================================================================
    // Email verification
    // =========================================================================

    @Transactional
    public void verifyEmail(String rawToken, HttpServletRequest request) {
        String hash = RefreshTokenService.sha256(rawToken);
        EmailVerificationToken t = verificationTokens.findByTokenHash(hash)
                .orElseThrow(() -> ApiException.badRequest(
                        ErrorCode.VERIFICATION_TOKEN_INVALID, "Invalid or expired verification token"));

        if (!t.isValid()) {
            throw ApiException.badRequest(ErrorCode.VERIFICATION_TOKEN_INVALID,
                    "Verification token has expired or already been used");
        }

        t.setUsedAt(OffsetDateTime.now());
        verificationTokens.save(t);

        User u = t.getUser();
        u.setEmailVerified(true);
        users.save(u);

        audit.log(AuditEventType.EMAIL_VERIFIED, u.getId(), request);
    }

    @Transactional
    public void resendVerification(UUID userId, HttpServletRequest request) {
        User u = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        if (u.isEmailVerified()) return; // already verified — silent success

        verificationTokens.deleteUnusedForUser(userId);
        String token = issueVerificationToken(u);
        email.sendVerificationEmail(u.getEmail(), displayName(u), token);
        audit.log(AuditEventType.EMAIL_VERIFICATION_SENT, userId, request);
    }

    // =========================================================================
    // Account deletion (KVKK right to erasure)
    // =========================================================================

    @Transactional
    public void deleteAccount(UUID userId, String password, HttpServletRequest request) {
        User u = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        // Require password confirmation for password accounts; skip for Google-only
        if (u.hasPassword() && !encoder.matches(password, u.getPasswordHash())) {
            throw ApiException.unauthorized("Password is incorrect");
        }

        // Revoke all tokens
        refreshTokens.revokeAll(userId);

        // Audit before anonymisation
        audit.log(AuditEventType.ACCOUNT_DELETED, userId, request,
                AuditService.details("email", u.getEmail()));

        // Anonymise PII (KVKK) — cascade-on-delete handles related rows in DB
        u.setEmail("deleted+" + userId + "@eczam.invalid");
        u.setDisplayName(null);
        u.setPasswordHash(null);
        u.setGoogleSub(null);
        u.setDeletedAt(OffsetDateTime.now());
        users.save(u);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private AuthResponse buildAuthResponse(User u, HttpServletRequest request) {
        String accessToken  = jwt.generateAccess(u.getId());
        String refreshToken = refreshTokens.issue(u, request);
        return new AuthResponse(toSummary(u), accessToken, refreshToken);
    }

    private UserSummary toSummary(User u) {
        return new UserSummary(
                u.getId().toString(),
                u.getEmail(),
                u.getDisplayName(),
                u.isEmailVerified(),
                u.getRole().name());
    }

    private void handleFailedLogin(User u) {
        int attempts = u.getFailedLoginAttempts() + 1;
        u.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            u.setLockedUntil(OffsetDateTime.now().plusMinutes(LOCKOUT_MINUTES));
            log.warn("Account locked for userId={} after {} failed attempts", u.getId(), attempts);
        }
        users.save(u);
    }

    private String issueVerificationToken(User u) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        EmailVerificationToken t = new EmailVerificationToken();
        t.setUser(u);
        t.setTokenHash(RefreshTokenService.sha256(raw));
        t.setExpiresAt(OffsetDateTime.now().plusHours(VERIFICATION_TOKEN_HOURS));
        verificationTokens.save(t);
        return raw;
    }

    private static String displayName(User u) {
        return u.getDisplayName() != null ? u.getDisplayName() : u.getEmail();
    }
}
