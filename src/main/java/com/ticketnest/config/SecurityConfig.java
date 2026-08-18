package com.ticketnest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Central Spring Security configuration.
 * Defines the SecurityFilterChain: a sequence of filters that process each HTTP request
 * for authentication, authorization, CSRF, security headers, etc.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * BCrypt password encoder for hashing user passwords.
     * Strength 10 is the default (2^10 rounds).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /**
     * Configures the filter chain:
     * - Permits unauthenticated access to /auth/** (login, register, token refresh)
     * - Requires authentication for all other endpoints
     * - Disables CSRF (stateless REST API)
     * - Enables HTTP Basic authentication
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Authorization rules: matchers evaluated in order
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**").permitAll()                    // public auth endpoints
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll() // Swagger/OpenAPI
                .anyRequest().authenticated()                               // everything else requires auth
            )
            .csrf(csrf -> csrf.disable())                  // disable CSRF for stateless API
            .httpBasic(httpBasic -> {});                   // enable HTTP Basic auth
        return http.build();
    }
}