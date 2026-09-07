package com.ticketnest.show.dto;

import java.util.UUID;

public record ShowInventoryResponse(UUID showId, String currency, int seatCount) {
}
