package com.ticketnest.show.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Request DTO for creating/updating a Show.
 * venueId must reference an existing Venue.
 */
public record ShowRequest(
        @NotNull UUID venueId,
        @NotBlank String title,
        @NotBlank String genre,
        @NotNull Instant startTime,
        @NotBlank String status
) {}