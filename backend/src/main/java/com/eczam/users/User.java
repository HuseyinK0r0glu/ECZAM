package com.eczam.users;

import com.eczam.shared.security.UserRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class User {

    @Id @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notification_preferences", nullable = false, columnDefinition = "jsonb")
    private NotificationPreferences notificationPreferences = NotificationPreferences.defaults();

    // ---- Auth enhancements (V4) ----

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    /** Google subject identifier for OAuth login. Null if not linked. */
    @Column(name = "google_sub", unique = true)
    private String googleSub;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    /** Soft-delete timestamp for KVKK erasure; null = active account. */
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    // ---- TOTP 2FA (V6) ----

    @Column(name = "totp_secret")
    private String totpSecret;

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled = false;

    @Column(name = "totp_backup_codes")
    private String[] totpBackupCodes;

    @Column(name = "totp_enrolled_at")
    private OffsetDateTime totpEnrolledAt;

    // ---- Timestamps ----

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // ---- Helpers ----

    public boolean isLocked() {
        return lockedUntil != null && OffsetDateTime.now().isBefore(lockedUntil);
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Password is irrelevant for Google-only accounts; null hash signals this. */
    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }
}
