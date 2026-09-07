package com.ticketnest.repository;

import com.ticketnest.entity.Booking;
import com.ticketnest.entity.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    Optional<Booking> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id IN :ids ORDER BY b.id")
    List<Booking> findAllByIdForUpdate(@Param("ids") Collection<UUID> ids);

    @Query(value = """
            SELECT id FROM bookings
            WHERE status = 'HELD' AND expires_at <= :now
            ORDER BY expires_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT :batchSize
            """, nativeQuery = true)
    List<UUID> findExpiredIdsForUpdate(@Param("now") Instant now, @Param("batchSize") int batchSize);

    @Query("""
            SELECT b.id FROM Booking b
            WHERE b.user.id = :userId
              AND b.status = com.ticketnest.entity.BookingStatus.HELD
              AND b.expiresAt <= :now
            ORDER BY b.id
            """)
    List<UUID> findExpiredIdsForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    Page<Booking> findByUserId(UUID userId, Pageable pageable);
    Page<Booking> findByUserIdAndStatus(UUID userId, BookingStatus status, Pageable pageable);
}
