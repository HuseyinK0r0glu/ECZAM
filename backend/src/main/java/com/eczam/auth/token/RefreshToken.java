package com.eczam.auth.token;

import com.eczam.users.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter @Setter @NoArgsConstructor
public class RefreshToken {

    @Id @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** SHA-256 hex of the raw opaque token sent to the client. */
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    /** All tokens in a rotation chain share the same family UUID. */
    @Column(nullable = false)
    private UUID family;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "ip_address")
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !revoked && !isExpired();
    }
}
