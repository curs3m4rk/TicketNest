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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(201).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenResponse response = authService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(response);
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