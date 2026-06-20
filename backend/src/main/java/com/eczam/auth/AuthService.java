package com.eczam.auth;

import com.eczam.auth.dto.AuthDtos.*;
import com.eczam.shared.security.JwtService;
import com.eczam.shared.web.ApiException;
import com.eczam.shared.web.ErrorCode;
import com.eczam.users.User;
import com.eczam.users.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (users.existsByEmail(req.email())) {
            throw ApiException.conflict(ErrorCode.EMAIL_TAKEN, "Email already registered");
        }
        User u = new User();
        u.setEmail(req.email().toLowerCase());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setDisplayName(req.displayName());
        users.save(u);
        return tokensFor(u);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User u = users.findByEmail(req.email().toLowerCase())
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password");
        }
        return tokensFor(u);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        UUID userId;
        try {
            userId = jwt.verify(refreshToken, JwtService.TokenType.REFRESH);
        } catch (Exception e) {
            throw ApiException.unauthorized("Invalid refresh token");
        }
        User u = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
        return tokensFor(u);
    }

    /** Always succeeds (non-enumerating). Returns a reset token to be emailed. */
    @Transactional(readOnly = true)
    public String requestReset(String email) {
        return users.findByEmail(email.toLowerCase())
                .map(u -> jwt.generateReset(u.getId()))
                .orElse(null); // caller still returns 204
    }

    @Transactional
    public void confirmReset(PasswordResetConfirm req) {
        UUID userId;
        try {
            userId = jwt.verify(req.token(), JwtService.TokenType.RESET);
        } catch (Exception e) {
            throw ApiException.badRequest(ErrorCode.RESET_TOKEN_INVALID, "Invalid or expired reset token");
        }
        User u = users.findById(userId)
                .orElseThrow(() -> ApiException.badRequest(ErrorCode.RESET_TOKEN_INVALID, "Invalid reset token"));
        u.setPasswordHash(encoder.encode(req.newPassword()));
    }

    private AuthResponse tokensFor(User u) {
        var summary = new UserSummary(u.getId().toString(), u.getEmail(), u.getDisplayName());
        return new AuthResponse(summary, jwt.generateAccess(u.getId()), jwt.generateRefresh(u.getId()));
    }
}
