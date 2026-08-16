package com.ticketnest.venue.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for creating/updating a Venue.
 * Validation ensures required fields are not blank.
 */
public record VenueRequest(
        @NotBlank String name,
        @NotBlank String city,
        @NotBlank String address
) {}