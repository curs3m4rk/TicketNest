package com.ticketnest.common.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryTokenBucketRateLimiterTest {
    private static final Instant NOW = Instant.parse("2027-01-10T10:00:00Z");

    private MutableClock clock;
    private InMemoryTokenBucketRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        limiter = limiter(2, 3);
    }

    @Test
    void permitsCapacityThenReturnsTimeUntilNextToken() {
        assertTrue(limiter.tryAcquire(RateLimitPolicy.LOGIN, "client").allowed());
        assertTrue(limiter.tryAcquire(RateLimitPolicy.LOGIN, "client").allowed());

        RateLimitDecision denied = limiter.tryAcquire(RateLimitPolicy.LOGIN, "client");

        assertFalse(denied.allowed());
        assertEquals(30, denied.retryAfterSeconds());
    }

    @Test
    void refillsContinuouslyAndRoundsRetryUpToWholeSeconds() {
        limiter.tryAcquire(RateLimitPolicy.LOGIN, "client");
        limiter.tryAcquire(RateLimitPolicy.LOGIN, "client");
        clock.advance(Duration.ofSeconds(15).plusMillis(1));

        RateLimitDecision denied = limiter.tryAcquire(RateLimitPolicy.LOGIN, "client");
        assertEquals(15, denied.retryAfterSeconds());

        clock.advance(Duration.ofSeconds(15));
        assertTrue(limiter.tryAcquire(RateLimitPolicy.LOGIN, "client").allowed());
    }

    @Test
    void refillNeverExceedsBurstCapacity() {
        limiter.tryAcquire(RateLimitPolicy.LOGIN, "client");
        limiter.tryAcquire(RateLimitPolicy.LOGIN, "client");
        clock.advance(Duration.ofMinutes(10));

        assertTrue(limiter.tryAcquire(RateLimitPolicy.LOGIN, "client").allowed());
        assertTrue(limiter.tryAcquire(RateLimitPolicy.LOGIN, "client").allowed());
        assertFalse(limiter.tryAcquire(RateLimitPolicy.LOGIN, "client").allowed());
    }

    @Test
    void identitiesAndPoliciesHaveIndependentBuckets() {
        limiter.tryAcquire(RateLimitPolicy.LOGIN, "first");
        limiter.tryAcquire(RateLimitPolicy.LOGIN, "first");

        assertFalse(limiter.tryAcquire(RateLimitPolicy.LOGIN, "first").allowed());
        assertTrue(limiter.tryAcquire(RateLimitPolicy.LOGIN, "second").allowed());
        assertTrue(limiter.tryAcquire(RateLimitPolicy.BOOKING_CREATE, "first").allowed());
        assertTrue(limiter.tryAcquire(RateLimitPolicy.BOOKING_CREATE, "first").allowed());
        assertTrue(limiter.tryAcquire(RateLimitPolicy.BOOKING_CREATE, "first").allowed());
        assertFalse(limiter.tryAcquire(RateLimitPolicy.BOOKING_CREATE, "first").allowed());
    }

    @Test
    void cleanupRemovesBucketsInactiveForAFullRefillPeriod() {
        limiter.tryAcquire(RateLimitPolicy.LOGIN, "client");
        assertEquals(1, limiter.trackedBucketCount());

        clock.advance(Duration.ofMinutes(1));
        limiter.removeStaleBuckets();

        assertEquals(0, limiter.trackedBucketCount());
    }

    @Test
    void concurrentRequestsCannotExceedCapacity() throws Exception {
        int capacity = 10;
        limiter = limiter(capacity, capacity);
        int requestCount = 50;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(requestCount);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return limiter.tryAcquire(RateLimitPolicy.LOGIN, "client").allowed();
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int allowed = 0;
            for (Future<Boolean> result : results) {
                if (result.get(5, TimeUnit.SECONDS)) {
                    allowed++;
                }
            }
            assertEquals(capacity, allowed);
        } finally {
            executor.shutdownNow();
        }
    }

    private InMemoryTokenBucketRateLimiter limiter(int loginCapacity, int bookingCapacity) {
        RateLimitProperties properties = new RateLimitProperties(
                new RateLimitProperties.Policy(loginCapacity, Duration.ofMinutes(1)),
                new RateLimitProperties.Policy(bookingCapacity, Duration.ofMinutes(1)),
                Duration.ofMinutes(1));
        return new InMemoryTokenBucketRateLimiter(properties, clock);
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        private MutableClock(Instant initial) {
            instant = new AtomicReference<>(initial);
        }

        void advance(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
