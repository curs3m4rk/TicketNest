package com.ticketnest.common.ratelimit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class InMemoryTokenBucketRateLimiter implements RateLimiter {
    private static final double NANOS_PER_SECOND = 1_000_000_000d;

    private final Map<BucketKey, BucketState> buckets = new ConcurrentHashMap<>();
    private final RateLimitProperties properties;
    private final Clock clock;

    public InMemoryTokenBucketRateLimiter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public RateLimitDecision tryAcquire(RateLimitPolicy policy, String identity) {
        if (identity == null || identity.isBlank()) {
            throw new IllegalArgumentException("Rate-limit identity is required");
        }

        RateLimitProperties.Policy configuration = properties.forPolicy(policy);
        Instant now = clock.instant();
        AtomicReference<RateLimitDecision> decision = new AtomicReference<>();
        buckets.compute(new BucketKey(policy, identity), (key, current) -> {
            BucketState state = current == null
                    ? new BucketState(configuration.capacity(), now, now)
                    : current;
            double tokens = refill(state, configuration, now);
            if (tokens >= 1d) {
                decision.set(RateLimitDecision.granted());
                return new BucketState(tokens - 1d, now, now);
            }

            double tokensPerNano = (double) configuration.capacity() / configuration.refillPeriod().toNanos();
            long nanosUntilToken = (long) Math.ceil((1d - tokens) / tokensPerNano);
            long retryAfterSeconds = Math.max(1L, (long) Math.ceil(nanosUntilToken / NANOS_PER_SECOND));
            decision.set(RateLimitDecision.denied(retryAfterSeconds));
            return new BucketState(tokens, now, now);
        });
        return decision.get();
    }

    @Scheduled(fixedDelayString = "${app.rate-limit.cleanup-interval:PT1M}")
    public void removeStaleBuckets() {
        Instant now = clock.instant();
        buckets.entrySet().removeIf(entry -> {
            Duration refillPeriod = properties.forPolicy(entry.getKey().policy()).refillPeriod();
            return !entry.getValue().lastAccess().plus(refillPeriod).isAfter(now);
        });
    }

    int trackedBucketCount() {
        return buckets.size();
    }

    private double refill(BucketState state, RateLimitProperties.Policy configuration, Instant now) {
        long elapsedNanos = Math.max(0L, Duration.between(state.lastRefill(), now).toNanos());
        double refill = elapsedNanos * ((double) configuration.capacity() / configuration.refillPeriod().toNanos());
        return Math.min(configuration.capacity(), state.tokens() + refill);
    }

    private record BucketKey(RateLimitPolicy policy, String identity) {
    }

    private record BucketState(double tokens, Instant lastRefill, Instant lastAccess) {
    }
}
