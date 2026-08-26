package com.ticketnest.show.dto;

import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

public record ShowFilter(
        @Size(max = 100, message = "City must be at most 100 characters")
        String city,

        @Size(max = 50, message = "Genre must be at most 50 characters")
        String genre,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant from,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant to
) {}
