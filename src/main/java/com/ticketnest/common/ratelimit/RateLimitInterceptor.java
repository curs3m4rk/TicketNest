package com.ticketnest.common.ratelimit;

import com.ticketnest.config.ApiPaths;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    private static final String LOGIN_PATH = ApiPaths.AUTH_V1 + "/login";
    private static final String BOOKING_CREATE_PATH = ApiPaths.V1 + "/bookings";

    private final RateLimiter rateLimiter;

    public RateLimitInterceptor(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (LOGIN_PATH.equals(path)) {
            enforce(RateLimitPolicy.LOGIN, request.getRemoteAddr());
        } else if (BOOKING_CREATE_PATH.equals(path)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && !(authentication instanceof AnonymousAuthenticationToken)) {
                enforce(RateLimitPolicy.BOOKING_CREATE, authentication.getName());
            }
        }
        return true;
    }

    private void enforce(RateLimitPolicy policy, String identity) {
        RateLimitDecision decision = rateLimiter.tryAcquire(policy, identity);
        if (!decision.allowed()) {
            throw new RateLimitExceededException(decision.retryAfterSeconds());
        }
    }
}
