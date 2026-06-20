package com.eczam.users;

import com.eczam.shared.web.ApiException;
import com.eczam.users.dto.UserDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository users;
    public UserService(UserRepository users) { this.users = users; }

    @Transactional(readOnly = true)
    public UserProfile getProfile(UUID userId) {
        return toProfile(load(userId));
    }

    @Transactional
    public UserProfile updateProfile(UUID userId, UpdateProfileRequest req) {
        User u = load(userId);
        if (req.displayName() != null) u.setDisplayName(req.displayName());
        return toProfile(u);
    }

    @Transactional
    public UserProfile updatePreferences(UUID userId, UpdatePreferencesRequest req) {
        User u = load(userId);
        NotificationPreferences cur = u.getNotificationPreferences();
        u.setNotificationPreferences(new NotificationPreferences(
                req.push() != null ? req.push() : cur.push(),
                req.email() != null ? req.email() : cur.email(),
                req.lowStockThreshold() != null ? req.lowStockThreshold() : cur.lowStockThreshold(),
                req.expiryWarningDays() != null ? req.expiryWarningDays() : cur.expiryWarningDays()));
        return toProfile(u);
    }

    private User load(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private UserProfile toProfile(User u) {
        return new UserProfile(u.getId().toString(), u.getEmail(), u.getDisplayName(),
                u.getNotificationPreferences());
    }
}
