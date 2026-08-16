package com.ticketnest.venue.dto;

import java.util.UUID;
import java.util.List;

/**
 * API response DTO for Venue.
 * Includes seat tiers aggregated from Seat entities for catalog display.
 */
public record VenueResponse(
        UUID id,
        String name,
        String city,
        String address,
        boolean active,
        List<String> seatTiers
) {}