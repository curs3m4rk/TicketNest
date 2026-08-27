package com.ticketnest.repository;

import com.ticketnest.entity.OtpChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

    long countByDestinationAndCreatedAtAfter(String destination, Instant after);

    Optional<OtpChallenge> findTopByDestinationOrderByCreatedAtDesc(String destination);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from OtpChallenge challenge where challenge.id = :id")
    Optional<OtpChallenge> findByIdForUpdate(UUID id);
}
