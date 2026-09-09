package com.ticketnest.common.ratelimit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimitInterceptorTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginUsesRemoteAddress() {
        RecordingRateLimiter limiter = new RecordingRateLimiter();
        MockHttpServletRequest request = request("POST", "/api/v1/auth/login");
        request.setRemoteAddr("203.0.113.10");

        new RateLimitInterceptor(limiter).preHandle(request, new MockHttpServletResponse(), new Object());

        assertEquals(List.of(new Attempt(RateLimitPolicy.LOGIN, "203.0.113.10")), limiter.attempts);
    }

    @Test
    void bookingCreateUsesAuthenticatedPrincipal() {
        RecordingRateLimiter limiter = new RecordingRateLimiter();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user@example.com", null, List.of()));

        new RateLimitInterceptor(limiter).preHandle(
                request("POST", "/api/v1/bookings"), new MockHttpServletResponse(), new Object());

        assertEquals(List.of(new Attempt(RateLimitPolicy.BOOKING_CREATE, "user@example.com")), limiter.attempts);
    }

    @Test
    void ignoresOtherMethodsEndpointsAndUnauthenticatedBookingRequests() {
        RecordingRateLimiter limiter = new RecordingRateLimiter();
        RateLimitInterceptor interceptor = new RateLimitInterceptor(limiter);

        assertDoesNotThrow(() -> interceptor.preHandle(
                request("GET", "/api/v1/auth/login"), new MockHttpServletResponse(), new Object()));
        assertDoesNotThrow(() -> interceptor.preHandle(
                request("POST", "/api/v1/auth/register"), new MockHttpServletResponse(), new Object()));
        assertDoesNotThrow(() -> interceptor.preHandle(
                request("POST", "/api/v1/bookings/id/cancel"), new MockHttpServletResponse(), new Object()));
        assertDoesNotThrow(() -> interceptor.preHandle(
                request("POST", "/api/v1/bookings"), new MockHttpServletResponse(), new Object()));

        assertEquals(List.of(), limiter.attempts);
    }

    @Test
    void denialRaisesExceptionWithRetryDelay() {
        RecordingRateLimiter limiter = new RecordingRateLimiter();
        limiter.decision = RateLimitDecision.denied(17);

        RateLimitExceededException exception = assertThrows(RateLimitExceededException.class,
                () -> new RateLimitInterceptor(limiter).preHandle(
                        request("POST", "/api/v1/auth/login"), new MockHttpServletResponse(), new Object()));

        assertEquals(17, exception.getRetryAfterSeconds());
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }

    private record Attempt(RateLimitPolicy policy, String identity) {
    }

    private static final class RecordingRateLimiter implements RateLimiter {
        private final List<Attempt> attempts = new ArrayList<>();
        private RateLimitDecision decision = RateLimitDecision.granted();

        @Override
        public RateLimitDecision tryAcquire(RateLimitPolicy policy, String identity) {
            attempts.add(new Attempt(policy, identity));
            return decision;
        }
    }
}
