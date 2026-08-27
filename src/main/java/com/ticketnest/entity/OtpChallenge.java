package com.ticketnest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "otp_challenges", indexes = {
        @Index(name = "idx_otp_destination_created", columnList = "destination,created_at")
})
@Getter
@Setter
public class OtpChallenge {

    @Id
    private UUID id;

    @Column(nullable = false, length = 254)
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OtpChannel channel;

    @Column(nullable = false, length = 44)
    private String codeHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private int maxAttempts;

    private Instant consumedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
