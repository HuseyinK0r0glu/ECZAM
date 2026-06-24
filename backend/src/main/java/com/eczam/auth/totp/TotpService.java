package com.eczam.auth.totp;

import com.eczam.shared.web.ApiException;
import com.eczam.shared.web.ErrorCode;
import com.eczam.users.User;
import com.eczam.users.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Time-based One-Time Password (TOTP) per RFC 6238.
 *
 * Flow:
 *  1. POST /auth/2fa/enroll  → returns a TOTP URI + QR data + backup codes
 *  2. POST /auth/2fa/confirm { code } → activates 2FA if code is valid
 *  3. POST /auth/login now returns a 202 TOTP_REQUIRED when 2FA is on
 *  4. POST /auth/2fa/verify { totpToken, code } → issues full JWT on success
 *  5. DELETE /auth/2fa       → disables 2FA (requires current password)
 *
 * The TOTP secret is stored in plain text (recommended: encrypt with app-level key at rest).
 */
@Slf4j
@Service
public class TotpService {

    private static final int TOTP_DIGITS   = 6;
    private static final int TOTP_PERIOD   = 30;   // seconds
    private static final int TOTP_WINDOW   = 1;    // allow ±1 step for clock skew
    private static final int BACKUP_COUNT  = 10;
    private static final int SECRET_BYTES  = 20;   // 160-bit secret (RFC 4226)

    // Base32 alphabet (RFC 4648)
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TotpPendingRepository pending;

    public TotpService(UserRepository users, PasswordEncoder encoder,
                       TotpPendingRepository pending) {
        this.users   = users;
        this.encoder = encoder;
        this.pending = pending;
    }

    // =========================================================================
    // Enrollment
    // =========================================================================

    /** Begin enrollment: generate a secret, store it as pending, return setup info. */
    @Transactional
    public EnrollmentResult beginEnrollment(UUID userId) {
        User u = load(userId);
        if (u.isTotpEnabled()) {
            throw ApiException.conflict("TOTP_ALREADY_ENABLED", "2FA is already enabled");
        }

        String secret = generateSecret();

        // Store pending (replaces any previous pending enrollment)
        pending.deleteByUserId(userId);
        TotpPendingEnrollment p = new TotpPendingEnrollment();
        p.setUserId(userId);
        p.setSecret(secret);
        p.setExpiresAt(OffsetDateTime.now().plusMinutes(30));
        pending.save(p);

        String uri = buildUri(u.getEmail(), secret);
        return new EnrollmentResult(secret, uri);
    }

    /** Confirm enrollment: verify the code matches the pending secret, activate 2FA. */
    @Transactional
    public List<String> confirmEnrollment(UUID userId, String code) {
        TotpPendingEnrollment p = pending.findByUserId(userId)
                .orElseThrow(() -> ApiException.badRequest("TOTP_NO_PENDING", "No pending 2FA enrollment"));

        if (p.getExpiresAt().isBefore(OffsetDateTime.now())) {
            pending.delete(p);
            throw ApiException.badRequest("TOTP_EXPIRED", "2FA enrollment expired — please restart enrollment");
        }

        if (!verifyCode(p.getSecret(), code)) {
            throw ApiException.badRequest("TOTP_INVALID_CODE", "Invalid verification code");
        }

        // Activate
        User u = load(userId);
        u.setTotpSecret(p.getSecret());
        u.setTotpEnabled(true);
        u.setTotpEnrolledAt(OffsetDateTime.now());

        // Generate and hash backup codes
        List<String> rawBackup = generateBackupCodes();
        u.setTotpBackupCodes(rawBackup.stream().map(encoder::encode).toArray(String[]::new));
        users.save(u);

        pending.delete(p);
        return rawBackup;
    }

    // =========================================================================
    // Verification (at login)
    // =========================================================================

    /**
     * Verify a TOTP code or a backup code.
     * @return true if valid
     */
    @Transactional
    public boolean verify(User u, String code) {
        if (!u.isTotpEnabled()) return true; // 2FA not required

        // Check TOTP code first
        if (verifyCode(u.getTotpSecret(), code)) return true;

        // Check backup codes
        return verifyBackupCode(u, code);
    }

    // =========================================================================
    // Disable 2FA
    // =========================================================================

    @Transactional
    public void disable(UUID userId, String password, String totpCode) {
        User u = load(userId);
        if (!u.isTotpEnabled()) {
            throw ApiException.badRequest("TOTP_NOT_ENABLED", "2FA is not enabled");
        }
        if (u.hasPassword() && !encoder.matches(password, u.getPasswordHash())) {
            throw ApiException.unauthorized("Password is incorrect");
        }
        if (!verifyCode(u.getTotpSecret(), totpCode)) {
            throw ApiException.badRequest("TOTP_INVALID_CODE", "Invalid TOTP code");
        }

        u.setTotpEnabled(false);
        u.setTotpSecret(null);
        u.setTotpBackupCodes(null);
        u.setTotpEnrolledAt(null);
        users.save(u);
    }

    // =========================================================================
    // Core TOTP algorithm (RFC 6238 / RFC 4226)
    // =========================================================================

    public boolean verifyCode(String base32Secret, String code) {
        if (code == null || code.length() != TOTP_DIGITS) return false;
        long currentStep = System.currentTimeMillis() / 1000L / TOTP_PERIOD;
        byte[] secret = base32Decode(base32Secret);
        for (int delta = -TOTP_WINDOW; delta <= TOTP_WINDOW; delta++) {
            if (generateTotp(secret, currentStep + delta).equals(code)) return true;
        }
        return false;
    }

    private String generateTotp(byte[] secret, long counter) {
        try {
            byte[] msg = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(msg);
            int offset = hash[hash.length - 1] & 0x0F;
            int code = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return String.format("%0" + TOTP_DIGITS + "d", code % (int) Math.pow(10, TOTP_DIGITS));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("TOTP computation failed", e);
        }
    }

    // =========================================================================
    // Backup codes
    // =========================================================================

    private boolean verifyBackupCode(User u, String raw) {
        String[] hashed = u.getTotpBackupCodes();
        if (hashed == null) return false;
        for (int i = 0; i < hashed.length; i++) {
            if (hashed[i] != null && encoder.matches(raw, hashed[i])) {
                hashed[i] = null; // invalidate used code
                users.save(u);
                return true;
            }
        }
        return false;
    }

    private List<String> generateBackupCodes() {
        SecureRandom rng = new SecureRandom();
        List<String> codes = new ArrayList<>(BACKUP_COUNT);
        for (int i = 0; i < BACKUP_COUNT; i++) {
            codes.add(String.format("%05d-%05d", rng.nextInt(100000), rng.nextInt(100000)));
        }
        return codes;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(bytes);
        return base32Encode(bytes);
    }

    private String buildUri(String email, String secret) {
        return "otpauth://totp/ECZAM:" + email + "?secret=" + secret + "&issuer=ECZAM&algorithm=SHA1&digits=6&period=30";
    }

    private User load(UUID id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
    }

    // Minimal Base32 encode/decode (no external library needed)
    private static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(BASE32.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) sb.append(BASE32.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        return sb.toString();
    }

    private static byte[] base32Decode(String encoded) {
        encoded = encoded.toUpperCase().replaceAll("[^A-Z2-7]", "");
        byte[] result = new byte[encoded.length() * 5 / 8];
        int buffer = 0, bitsLeft = 0, idx = 0;
        for (char c : encoded.toCharArray()) {
            int val = BASE32.indexOf(c);
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[idx++] = (byte) (buffer >> (bitsLeft - 8));
                bitsLeft -= 8;
            }
        }
        return result;
    }

    public record EnrollmentResult(String secret, String otpAuthUri) {}
}
