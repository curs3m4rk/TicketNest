package com.ticketnest.show.dto;

import java.util.UUID;
import java.time.Instant;
import java.util.List;

/**
 * API response DTO for Show — keeps JPA entities separate from API contract.
 * Includes seat tiers from the venue for catalog display.
 */
public record ShowResponse(
        UUID id,
        String title,
        String genre,
        Instant startTime,
        String status,
        VenueSummary venue
) {
    /**
     * Nested venue summary with seat tiers (e.g., ["VIP", "PREMIUM", "STANDARD"]).
     */
    public record VenueSummary(
            UUID id,
            String name,
            String city,
            String address,
            List<String> seatTiers
    ) {}
}