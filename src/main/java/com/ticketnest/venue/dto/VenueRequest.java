package com.ticketnest.venue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating/updating a Venue.
 * Validation ensures required fields are not blank.
 */
public record VenueRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String name,

        @NotBlank(message = "City is required")
        @Size(min = 2, max = 100, message = "City must be between 2 and 100 characters")
        String city,

        @NotBlank(message = "Address is required")
        @Size(min = 5, max = 255, message = "Address must be between 5 and 255 characters")
        String address
) {}
