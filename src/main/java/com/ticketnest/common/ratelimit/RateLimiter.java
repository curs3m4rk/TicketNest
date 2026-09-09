package com.ticketnest.common.ratelimit;

public interface RateLimiter {
    RateLimitDecision tryAcquire(RateLimitPolicy policy, String identity);
}
