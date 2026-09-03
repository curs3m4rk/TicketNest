package com.ticketnest.venue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SeatRangeRequest(
        @NotBlank(message = "Row is required")
        @Size(max = 255, message = "Row must not exceed 255 characters")
        String row,

        @NotNull(message = "Start number is required")
        @Positive(message = "Start number must be positive")
        Integer startNumber,

        @NotNull(message = "End number is required")
        @Positive(message = "End number must be positive")
        Integer endNumber,

        @NotBlank(message = "Tier is required")
        @Size(max = 255, message = "Tier must not exceed 255 characters")
        String tier
) {}
