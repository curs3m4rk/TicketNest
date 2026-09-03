package com.ticketnest.venue.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SeatBatchCreateRequest(
        @NotEmpty(message = "At least one seat range is required")
        List<@NotNull(message = "Seat range is required") @Valid SeatRangeRequest> ranges
) {}
