package com.ticketnest.auth;

import com.ticketnest.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String email = jwtUtil.getEmail(authHeader.substring(7));
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                var user = userRepository.findWithRolesByEmail(email)
                        .filter(com.ticketnest.entity.User::isActive)
                        .orElseThrow(() -> new IllegalArgumentException("User is missing or inactive"));
                var authorities = user.getRoles().stream()
                        .flatMap(role -> Stream.concat(
                                Stream.of(new SimpleGrantedAuthority("ROLE_" + role.getName())),
                                role.getPermissions().stream().map(permission -> new SimpleGrantedAuthority(permission.name()))))
                        .distinct()
                        .toList();
                var authentication = new UsernamePasswordAuthenticationToken(email, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception exception) {
            log.warn("JWT authentication failed: {}", exception.getMessage());
        }
        filterChain.doFilter(request, response);
    }
}
