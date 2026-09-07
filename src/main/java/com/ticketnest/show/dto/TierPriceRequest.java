package com.ticketnest.show.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TierPriceRequest(
        @NotBlank(message = "Tier is required")
        @Size(max = 255, message = "Tier must not exceed 255 characters")
        String tier,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be at least 0.01")
        @Digits(integer = 10, fraction = 2, message = "Price must have at most 10 integer and 2 fractional digits")
        BigDecimal price
) {
}
