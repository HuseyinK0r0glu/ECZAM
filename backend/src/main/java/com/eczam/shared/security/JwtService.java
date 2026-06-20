package com.eczam.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    public enum TokenType { ACCESS, REFRESH, RESET }

    private final JwtProperties props;
    private final SecretKey key;

    public JwtService(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccess(UUID userId)  { return generate(userId, TokenType.ACCESS,  props.accessTtl().toSeconds()); }
    public String generateRefresh(UUID userId) { return generate(userId, TokenType.REFRESH, props.refreshTtl().toSeconds()); }
    public String generateReset(UUID userId)   { return generate(userId, TokenType.RESET,   props.resetTtl().toSeconds()); }

    private String generate(UUID userId, TokenType type, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", type.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    /** Returns the subject (userId) if valid and of the expected type, else throws. */
    public UUID verify(String token, TokenType expected) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        if (!expected.name().equals(claims.get("type", String.class))) {
            throw new IllegalArgumentException("Wrong token type");
        }
        return UUID.fromString(claims.getSubject());
    }
}
