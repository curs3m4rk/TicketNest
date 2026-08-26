package com.ticketnest.show.dto;

import java.time.Instant;

public record ShowFilter(
        String city,
        String genre,
        Instant from,
        Instant to
) {}
