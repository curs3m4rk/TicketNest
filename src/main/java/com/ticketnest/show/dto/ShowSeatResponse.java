package com.ticketnest.show.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ShowSeatResponse(
        UUID id,
        UUID sourceSeatId,
        String row,
        String number,
        String tier,
        BigDecimal price,
        String currency,
        String availability
) {
}
