package com.ticketnest.show.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Request DTO for creating/updating a Show.
 * venueId must reference an existing Venue.
 */
public record ShowRequest(
        @NotNull(message = "Venue ID is required") UUID venueId,

        @NotBlank(message = "Title is required")
        @Size(min = 2, max = 150, message = "Title must be between 2 and 150 characters")
        String title,

        @NotBlank(message = "Genre is required")
        @Size(min = 2, max = 50, message = "Genre must be between 2 and 50 characters")
        String genre,

        @NotNull(message = "Start time is required") Instant startTime,

        @NotBlank(message = "Status is required")
        @Size(min = 2, max = 30, message = "Status must be between 2 and 30 characters")
        String status
) {}
