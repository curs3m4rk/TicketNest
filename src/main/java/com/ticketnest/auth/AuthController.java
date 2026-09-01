package com.ticketnest.auth;

import com.ticketnest.auth.dto.LoginRequest;
import com.ticketnest.auth.dto.LoginResponse;
import com.ticketnest.auth.dto.LogoutRequest;
import com.ticketnest.auth.dto.RefreshRequest;
import com.ticketnest.auth.dto.RegisterRequest;
import com.ticketnest.auth.dto.RegisterResponse;
import com.ticketnest.auth.dto.TokenResponse;
import com.ticketnest.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

/**
 * Public authentication endpoints. No security (permitted by SecurityFilterChain).
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        try {
            RegisterResponse response = authService.register(request);
            return ResponseEntity.status(201).body(response);
        } catch (IllegalArgumentException | DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        try {
            TokenResponse response = authService.refresh(request.getRefreshToken());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(null);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody(required = false) LogoutRequest request,
                                       @AuthenticationPrincipal String email) {
        UUID userId = userRepository.findByEmail(email)
                .map(u -> u.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String refreshToken = (request != null) ? request.getRefreshToken() : null;
        authService.logout(userId, refreshToken);
        return ResponseEntity.noContent().build();
    }
}