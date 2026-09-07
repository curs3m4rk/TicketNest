package com.ticketnest.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record BookingCreateRequest(
        @NotNull(message = "Show ID is required") UUID showId,
        @NotNull(message = "Show seat IDs are required")
        @Size(min = 1, max = 10, message = "Between 1 and 10 show seats are required")
        List<@NotNull(message = "Show seat ID is required") UUID> showSeatIds
) {
}
