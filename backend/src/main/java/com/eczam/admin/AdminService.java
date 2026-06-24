package com.eczam.admin;

import com.eczam.audit.AuditEventType;
import com.eczam.audit.AuditLog;
import com.eczam.audit.AuditLogRepository;
import com.eczam.audit.AuditService;
import com.eczam.auth.token.RefreshTokenService;
import com.eczam.shared.web.ApiException;
import com.eczam.shared.web.ErrorCode;
import com.eczam.users.User;
import com.eczam.users.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AdminService {

    private final UserRepository users;
    private final AuditLogRepository auditLogs;
    private final AuditService audit;
    private final RefreshTokenService refreshTokens;

    public AdminService(UserRepository users, AuditLogRepository auditLogs,
                        AuditService audit, RefreshTokenService refreshTokens) {
        this.users = users;
        this.auditLogs = auditLogs;
        this.audit = audit;
        this.refreshTokens = refreshTokens;
    }

    // ---- User management ----

    @Transactional(readOnly = true)
    public Page<User> listUsers(String search, int page, int size) {
        PageRequest pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (search != null && !search.isBlank()) {
            return users.searchActive(search.strip(), pr);
        }
        return users.findAllActive(pr);
    }

    @Transactional(readOnly = true)
    public User getUser(UUID userId) {
        return users.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    @Transactional
    public void lockUser(UUID targetId, int minutes, UUID adminId, HttpServletRequest request) {
        User u = getUser(targetId);
        u.setLockedUntil(OffsetDateTime.now().plusMinutes(minutes));
        users.save(u);
        audit.log(AuditEventType.ADMIN_USER_LOCK, adminId, request,
                AuditService.details("targetUserId", targetId.toString(), "minutes", minutes));
    }

    @Transactional
    public void unlockUser(UUID targetId, UUID adminId, HttpServletRequest request) {
        User u = getUser(targetId);
        u.setLockedUntil(null);
        u.setFailedLoginAttempts(0);
        users.save(u);
        audit.log(AuditEventType.ADMIN_USER_UNLOCK, adminId, request,
                AuditService.details("targetUserId", targetId.toString()));
    }

    @Transactional
    public void deleteUser(UUID targetId, UUID adminId, HttpServletRequest request) {
        User u = getUser(targetId);

        audit.log(AuditEventType.ADMIN_USER_DELETE, adminId, request,
                AuditService.details("targetEmail", u.getEmail()));

        refreshTokens.revokeAll(targetId);

        // Anonymise PII
        u.setEmail("deleted+" + targetId + "@eczam.invalid");
        u.setDisplayName(null);
        u.setPasswordHash(null);
        u.setGoogleSub(null);
        u.setDeletedAt(OffsetDateTime.now());
        users.save(u);
    }

    // ---- Audit log access ----

    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogs(UUID userId, String eventType,
                                       OffsetDateTime from, OffsetDateTime to,
                                       int page, int size) {
        PageRequest pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return auditLogs.search(userId, eventType, from, to, pr);
    }
}
