package com.ticketnest.config;

import com.ticketnest.auth.JwtAuthenticationFilter;
import com.ticketnest.auth.UserDetailsServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
     * Returns 401 Unauthorized for unauthenticated requests to protected endpoints.
     * Used instead of redirecting to login page (stateless API).
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) ->
                response.sendError(401, "Unauthorized: " + authException.getMessage());
    }

    /**
     * Exposes custom UserDetailsService for @AuthenticationPrincipal and authentication manager.
     */
    @Bean
    public UserDetailsService userDetailsService(UserDetailsServiceImpl impl) {
        return impl;
    }

    /**
     * Configures the filter chain for JWT-based stateless authentication:
     * - Permits unauthenticated access to /auth/**, Swagger, OpenAPI docs
     * - Requires authentication for all other endpoints
     * - Disables CSRF (stateless REST API)
     * - Stateless session management (no HttpSession)
     * - Adds JWT filter before UsernamePasswordAuthenticationFilter
     * - Custom 401 entry point
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http
            // Authorization rules: matchers evaluated in order
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/logout").authenticated()              // logout requires valid token
                .requestMatchers("/auth/**").permitAll()                      // other auth endpoints public
                .requestMatchers("/swagger", "/swagger/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll() // Swagger/OpenAPI
                .anyRequest().authenticated()                                 // everything else requires auth
            )
            .csrf(csrf -> csrf.disable())                                   // disable CSRF for stateless API
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)     // no HttpSession
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint())       // 401 for unauthenticated
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class); // JWT validation
        return http.build();
    }
}