package com.eczam.users;

import com.eczam.shared.security.CurrentUser;
import com.eczam.shared.web.ApiResponse;
import com.eczam.users.dto.UserDtos.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService users;
    public UserController(UserService users) { this.users = users; }

    @GetMapping("/me")
    public ApiResponse<UserProfile> me(@CurrentUser UUID userId) {
        return ApiResponse.ok(users.getProfile(userId));
    }

    @PatchMapping("/me")
    public ApiResponse<UserProfile> updateMe(@CurrentUser UUID userId,
                                             @Valid @RequestBody UpdateProfileRequest req) {
        return ApiResponse.ok(users.updateProfile(userId, req));
    }

    @PatchMapping("/me/preferences")
    public ApiResponse<UserProfile> updatePrefs(@CurrentUser UUID userId,
                                                @Valid @RequestBody UpdatePreferencesRequest req) {
        return ApiResponse.ok(users.updatePreferences(userId, req));
    }
}
