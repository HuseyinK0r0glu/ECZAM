package com.eczam.auth.oauth;

import com.eczam.shared.web.ApiException;
import com.eczam.shared.web.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.Map;

/**
 * Verifies a Google ID token issued by Google Identity Services (frontend flow).
 *
 * The frontend signs in with Google and sends the resulting ID token here.
 * We verify it against Google's tokeninfo endpoint (simple, no JWK caching needed at MVP).
 * For high-volume production, switch to local JWK verification to avoid the network round-trip.
 *
 * EDIT THIS — set GOOGLE_CLIENT_ID to your actual OAuth 2.0 client ID from Google Cloud Console.
 */
@Slf4j
@Service
public class GoogleOAuthService {

    // EDIT THIS — replace with your real Google OAuth client ID from Google Cloud Console
    private static final String GOOGLE_CLIENT_ID = "";

    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo";

    private final RestClient rest;
    private final ObjectMapper mapper;
    private final String configuredClientId;

    public GoogleOAuthService(
            @Value("${eczam.google.client-id:}") String clientId) {
        this.configuredClientId = clientId.isBlank() ? GOOGLE_CLIENT_ID : clientId;
        this.rest = RestClient.create();
        this.mapper = new ObjectMapper();
    }

    /**
     * Verifies the Google ID token and returns the Google user's info.
     * Throws 401 if the token is invalid or the audience does not match.
     */
    public GoogleUserInfo verify(String idToken) {
        if (configuredClientId == null || configuredClientId.isBlank()) {
            throw ApiException.of(HttpStatus.SERVICE_UNAVAILABLE,
                    ErrorCode.GOOGLE_NOT_CONFIGURED,
                    "Google OAuth is not configured on this server");
        }

        try {
            // Use Google's tokeninfo endpoint to verify the token
            String url = TOKENINFO_URL + "?id_token=" + idToken;
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = rest.get()
                    .uri(url)
                    .retrieve()
                    .body(Map.class);

            if (claims == null) throw new RuntimeException("Empty tokeninfo response");

            // Validate audience
            String aud = (String) claims.get("aud");
            if (!configuredClientId.equals(aud)) {
                log.warn("Google token audience mismatch: expected={} got={}", configuredClientId, aud);
                throw ApiException.unauthorized("Google token audience mismatch");
            }

            String sub   = (String) claims.get("sub");
            String email = (String) claims.get("email");
            String name  = (String) claims.getOrDefault("name", email);
            boolean emailVerified = Boolean.parseBoolean(
                    claims.getOrDefault("email_verified", "false").toString());

            if (sub == null || email == null) {
                throw new RuntimeException("Missing required claims in Google token");
            }

            return new GoogleUserInfo(sub, email.toLowerCase(), name, emailVerified);

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Google token verification failed: {}", e.getMessage());
            throw ApiException.unauthorized("Invalid Google ID token");
        }
    }

    public record GoogleUserInfo(String sub, String email, String name, boolean emailVerified) {}
}
