package com.ticketnest.venue.dto;

import java.util.UUID;

public record SeatBatchCreateResponse(
        UUID venueId,
        int createdCount
) {}

