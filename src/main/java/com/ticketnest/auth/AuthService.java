package com.ticketnest.auth;

import com.ticketnest.entity.RefreshToken;
import com.ticketnest.entity.Role;
import com.ticketnest.entity.User;
import com.ticketnest.repository.RefreshTokenRepository;
import com.ticketnest.repository.UserRepository;
import com.ticketnest.auth.dto.LoginRequest;
import com.ticketnest.auth.dto.LoginResponse;
import com.ticketnest.auth.dto.RegisterRequest;
import com.ticketnest.auth.dto.RegisterResponse;
import com.ticketnest.auth.dto.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Authentication business logic: registration, login, token refresh, logout.
 * Handles BCrypt password hashing, JWT access tokens, and opaque refresh tokens.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // Token configuration
    private static final int REFRESH_TOKEN_BYTES = 32;     // 256-bit opaque token
    private static final long REFRESH_TOKEN_TTL_DAYS = 30; // 30 days
    private static final String HASH_ALGO = "SHA-256";

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new IllegalArgumentException("Phone number already registered");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setRole(Role.USER);
        user.setActive(true);
        user.setCreatedAt(Instant.now());

        User saved = userRepository.save(user);
        return toRegisterResponse(saved);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!user.isActive()) {
            throw new IllegalArgumentException("Account deactivated");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        return issueLoginTokens(user);
    }

    /**
     * Refreshes access token using a valid refresh token.
     * Implements rotation: revokes used refresh token, issues new pair.
     */
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        String tokenHash = hashToken(refreshToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (stored.isRevoked()) {
            // Token reuse detected — revoke all user tokens (potential theft)
            revokeAllUserTokens(stored.getUser().getId());
            throw new IllegalArgumentException("Refresh token revoked");
        }

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Refresh token expired");
        }

        User user = stored.getUser();
        if (!user.isActive()) {
            throw new IllegalArgumentException("Account deactivated");
        }

        // Rotate: revoke old, issue new
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return issueRefreshTokens(user);
    }

    /**
     * Logout: revokes the specific refresh token (or all if token not provided).
     */
    @Transactional
    public void logout(UUID userId, String refreshToken) {
        if (refreshToken != null) {
            String tokenHash = hashToken(refreshToken);
            refreshTokenRepository.findByTokenHash(tokenHash)
                    .filter(rt -> rt.getUser().getId().equals(userId))
                    .ifPresent(rt -> {
                        rt.setRevoked(true);
                        refreshTokenRepository.save(rt);
                    });
        } else {
            revokeAllUserTokens(userId);
        }
    }

    @Transactional(readOnly = true)
    public void cleanupExpired() {
        refreshTokenRepository.deleteExpired(Instant.now());
    }

    private LoginResponse issueLoginTokens(User user) {
        String accessToken = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        String refreshToken = generateOpaqueToken();
        saveRefreshToken(user, refreshToken);

        LoginResponse response = new LoginResponse();
        response.setToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setPhoneNumber(user.getPhoneNumber());
        response.setRole(user.getRole());
        return response;
    }

    private TokenResponse issueRefreshTokens(User user) {
        String accessToken = jwtUtil.generateToken(user.getEmail(), user.getRole().name());
        String refreshToken = generateOpaqueToken();
        saveRefreshToken(user, refreshToken);

        TokenResponse response = new TokenResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        return response;
    }

    private void saveRefreshToken(User user, String refreshToken) {
        RefreshToken rt = new RefreshToken();
        rt.setTokenHash(hashToken(refreshToken));
        rt.setUser(user);
        rt.setExpiresAt(Instant.now().plusSeconds(REFRESH_TOKEN_TTL_DAYS * 86400));
        rt.setRevoked(false);
        refreshTokenRepository.save(rt);
    }

    private void revokeAllUserTokens(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGO);
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private RegisterResponse toRegisterResponse(User user) {
        RegisterResponse response = new RegisterResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setPhoneNumber(user.getPhoneNumber());
        response.setRole(user.getRole());
        return response;
    }
}