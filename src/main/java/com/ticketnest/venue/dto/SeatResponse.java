package com.ticketnest.venue.dto;

import java.util.UUID;

public record SeatResponse(
        UUID id,
        String row,
        String number,
        String tier
) {}
