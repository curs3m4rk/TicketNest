package com.ticketnest.common.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        @NotNull @Valid Policy login,
        @NotNull @Valid Policy bookingCreate,
        @NotNull Duration cleanupInterval
) {
    public RateLimitProperties {
        requirePositive(cleanupInterval, "cleanup-interval");
    }

    public Policy forPolicy(RateLimitPolicy policy) {
        return switch (policy) {
            case LOGIN -> login;
            case BOOKING_CREATE -> bookingCreate;
        };
    }

    public record Policy(@Positive int capacity, @NotNull Duration refillPeriod) {
        public Policy {
            requirePositive(refillPeriod, "refill-period");
        }
    }

    private static void requirePositive(Duration duration, String name) {
        if (duration != null && (duration.isZero() || duration.isNegative())) {
            throw new IllegalArgumentException("Rate-limit " + name + " must be positive");
        }
    }
}
