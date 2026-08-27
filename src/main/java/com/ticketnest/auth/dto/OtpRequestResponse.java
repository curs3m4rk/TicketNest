package com.ticketnest.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class OtpRequestResponse {
    private UUID challengeId;
    private Instant expiresAt;
}
