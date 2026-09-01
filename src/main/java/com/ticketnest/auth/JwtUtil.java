package com.ticketnest.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT token generation and validation.
 * Uses HS256 with a secret key from config (application.yml).
 */
@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }

    /**
     * Generates a JWT for the given user email and role.
     * Includes: subject=email, role claim, issuedAt, expiration.
     */
    public String generateToken(String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Validates token signature and expiration.
     * Returns Claims if valid, throws exception if invalid/expired.
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extracts email (subject) from token.
     */
    public String getEmail(String token) {
        return parseToken(token).getSubject();
    }

    /**
     * Extracts role claim from token.
     */
    public String getRole(String token) {
        return parseToken(token).get("role", String.class);
    }

    /**
     * Checks if token is expired.
     */
    public boolean isExpired(String token) {
        return parseToken(token).getExpiration().before(new Date());
    }
}