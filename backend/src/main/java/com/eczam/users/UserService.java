package com.eczam.users;

import com.eczam.audit.AuditEventType;
import com.eczam.audit.AuditService;
import com.eczam.auth.email.EmailService;
import com.eczam.auth.token.EmailVerificationTokenRepository;
import com.eczam.auth.token.RefreshTokenService;
import com.eczam.shared.web.ApiException;
import com.eczam.shared.web.ErrorCode;
import com.eczam.users.dto.UserDtos.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final EmailService email;
    private final AuditService audit;
    private final RefreshTokenService refreshTokens;
    private final EmailVerificationTokenRepository verificationTokens;

    public UserService(UserRepository users, PasswordEncoder encoder,
                       EmailService email, AuditService audit,
                       RefreshTokenService refreshTokens,
                       EmailVerificationTokenRepository verificationTokens) {
        this.users = users;
        this.encoder = encoder;
        this.email = email;
        this.audit = audit;
        this.refreshTokens = refreshTokens;
        this.verificationTokens = verificationTokens;
    }

    @Transactional(readOnly = true)
    public UserProfile getProfile(UUID userId) {
        return toProfile(load(userId));
    }

    @Transactional
    public UserProfile updateProfile(UUID userId, UpdateProfileRequest req) {
        User u = load(userId);
        if (req.displayName() != null) u.setDisplayName(req.displayName().strip());
        return toProfile(u);
    }

    @Transactional
    public UserProfile updatePreferences(UUID userId, UpdatePreferencesRequest req) {
        User u = load(userId);
        NotificationPreferences cur = u.getNotificationPreferences();
        u.setNotificationPreferences(new NotificationPreferences(
                req.push()             != null ? req.push()             : cur.push(),
                req.email()            != null ? req.email()            : cur.email(),
                req.lowStockThreshold() != null ? req.lowStockThreshold() : cur.lowStockThreshold(),
                req.expiryWarningDays() != null ? req.expiryWarningDays() : cur.expiryWarningDays()));
        return toProfile(u);
    }

    /**
     * Initiate email change: verifies current password, sends verification to new address.
     * The change is committed only after the user clicks the verification link.
     * (For simplicity at MVP: we update the email directly and reset emailVerified.)
     */
    @Transactional
    public void changeEmail(UUID userId, ChangeEmailRequest req, HttpServletRequest request) {
        String newEmail = req.newEmail().toLowerCase().strip();
        User u = load(userId);

        if (!u.hasPassword()) {
            throw ApiException.badRequest(ErrorCode.CURRENT_PASSWORD_WRONG,
                    "Google-linked accounts cannot change email this way");
        }
        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            throw ApiException.unauthorized("Password is incorrect");
        }
        if (users.existsByEmail(newEmail)) {
            throw ApiException.conflict(ErrorCode.EMAIL_TAKEN, "Email already in use");
        }

        u.setEmail(newEmail);
        u.setEmailVerified(false);
        users.save(u);

        // Revoke all sessions — user must re-login with new email
        refreshTokens.revokeAll(userId);

        // Issue a new verification for the new address
        String verToken = issueVerificationToken(u);
        email.sendVerificationEmail(u.getEmail(), u.getDisplayName() != null ? u.getDisplayName() : u.getEmail(), verToken);
        audit.log(AuditEventType.PROFILE_UPDATED, userId, request,
                AuditService.details("field", "email", "newEmail", newEmail));
    }

    // -------------------------------------------------------------------------

    private User load(UUID id) {
        return users.findById(id)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private UserProfile toProfile(User u) {
        return new UserProfile(
                u.getId().toString(),
                u.getEmail(),
                u.getDisplayName(),
                u.isEmailVerified(),
                u.getRole().name(),
                u.hasPassword(),
                u.getGoogleSub() != null,
                u.getNotificationPreferences());
    }

    private String issueVerificationToken(User u) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        com.eczam.auth.token.EmailVerificationToken t = new com.eczam.auth.token.EmailVerificationToken();
        t.setUser(u);
        t.setTokenHash(RefreshTokenService.sha256(raw));
        t.setExpiresAt(OffsetDateTime.now().plusHours(24));
        verificationTokens.save(t);
        return raw;
    }
}
