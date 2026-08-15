package com.ticketnest.show.dto;

import java.util.UUID;
import java.time.Instant;

// API response DTO — keeps JPA entities separate from our API contract.
public record ShowResponse(
        UUID id,
        String title,
        String genre,
        Instant startTime,
        String status,
        VenueSummary venue
) {
    public record VenueSummary(
            UUID id,
            String name,
            String city,
            String address
    ) {}
}