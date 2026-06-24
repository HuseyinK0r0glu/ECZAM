package com.eczam.auth.totp;

import com.eczam.shared.security.CurrentUser;
import com.eczam.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "2FA (TOTP)", description = "Two-factor authentication enrollment and management using RFC 6238 TOTP")
@RestController
@RequestMapping("/auth/2fa")
public class TotpController {

    private final TotpService totp;

    public TotpController(TotpService totp) { this.totp = totp; }

    @Operation(summary = "Begin 2FA enrollment",
               description = "Generates a TOTP secret and returns an `otpauth://` URI. " +
                             "Render it as a QR code for the user to scan with Google Authenticator, Authy, etc. " +
                             "The secret is **not** active until confirmed via POST /auth/2fa/confirm.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Returns `secret` (Base32) and `otpAuthUri` — use the URI to generate a QR code"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "2FA is already enabled on this account", content = @Content)
    })
    @PostMapping("/enroll")
    public com.eczam.shared.web.ApiResponse<TotpService.EnrollmentResult> enroll(@CurrentUser UUID userId) {
        return com.eczam.shared.web.ApiResponse.ok(totp.beginEnrollment(userId));
    }

    @Operation(summary = "Confirm 2FA enrollment",
               description = "Verifies the code from the authenticator app against the pending secret. " +
                             "On success, 2FA is activated and 10 single-use backup codes are returned — " +
                             "display them once and instruct the user to store them safely.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "2FA enabled — returns 10 backup codes (shown **once only**)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "No pending enrollment, enrollment expired, or code is wrong", content = @Content)
    })
    @PostMapping("/confirm")
    public com.eczam.shared.web.ApiResponse<List<String>> confirm(@CurrentUser UUID userId,
                                                                   @Valid @RequestBody ConfirmRequest req) {
        return com.eczam.shared.web.ApiResponse.ok(totp.confirmEnrollment(userId, req.code()));
    }

    @Operation(summary = "Disable 2FA",
               description = "Requires both the account password (if set) and a valid TOTP code or backup code.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "2FA disabled"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "2FA not enabled or TOTP code invalid", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Password incorrect", content = @Content)
    })
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(@CurrentUser UUID userId,
                        @Valid @RequestBody DisableRequest req) {
        totp.disable(userId, req.password(), req.totpCode());
    }

    // ── Request records ──────────────────────────────────────────────────────

    public record ConfirmRequest(@NotBlank @Size(min = 6, max = 6) String code) {}

    public record DisableRequest(
            String password,   // null for Google-only accounts
            @NotBlank @Size(min = 6, max = 6) String totpCode) {}
}
