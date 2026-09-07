package com.ticketnest.show.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record ShowInventoryRequest(
        @NotBlank(message = "Currency is required")
        @Pattern(regexp = "(?i)^[A-Z]{3}$", message = "Currency must be a three-letter ISO code")
        String currency,

        @NotEmpty(message = "At least one tier price is required")
        List<@NotNull(message = "Tier price is required") @Valid TierPriceRequest> tierPrices
) {
}
