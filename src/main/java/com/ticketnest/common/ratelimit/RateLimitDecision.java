package com.ticketnest.common.ratelimit;

public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
    public static RateLimitDecision granted() {
        return new RateLimitDecision(true, 0);
    }

    public static RateLimitDecision denied(long retryAfterSeconds) {
        return new RateLimitDecision(false, Math.max(1, retryAfterSeconds));
    }
}
