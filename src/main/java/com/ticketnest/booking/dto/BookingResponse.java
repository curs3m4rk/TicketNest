package com.ticketnest.booking.dto;

import com.ticketnest.entity.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID showId,
        BookingStatus status,
        BigDecimal totalAmount,
        String currency,
        Instant createdAt,
        Instant expiresAt,
        List<Seat> seats
) {
    public record Seat(UUID showSeatId, UUID sourceSeatId, String row, String number, String tier, BigDecimal unitPrice) {
    }
}
