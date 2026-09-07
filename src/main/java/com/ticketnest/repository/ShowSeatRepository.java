package com.ticketnest.repository;

import com.ticketnest.entity.ShowSeat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, UUID> {

    @Query("""
            SELECT ss FROM ShowSeat ss
            JOIN FETCH ss.inventory inventory
            JOIN FETCH ss.sourceSeat
            WHERE inventory.showId = :showId
            """)
    List<ShowSeat> findAllByShowId(@Param("showId") UUID showId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT ss FROM ShowSeat ss
            JOIN FETCH ss.inventory inventory
            JOIN FETCH ss.sourceSeat
            WHERE inventory.showId = :showId AND ss.id IN :ids
            ORDER BY ss.id
            """)
    List<ShowSeat> findRequestedForUpdate(@Param("showId") UUID showId, @Param("ids") Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ss FROM ShowSeat ss WHERE ss.allocatedBooking.id = :bookingId ORDER BY ss.id")
    List<ShowSeat> findAllocatedForUpdate(@Param("bookingId") UUID bookingId);

    @Query("""
            SELECT DISTINCT ss.allocatedBooking.id FROM ShowSeat ss
            WHERE ss.inventory.showId = :showId
              AND ss.status = com.ticketnest.entity.ShowSeatStatus.HELD
              AND ss.holdExpiresAt <= :now
            """)
    List<UUID> findExpiredBookingIdsForShow(@Param("showId") UUID showId, @Param("now") Instant now);

    @Query("""
            SELECT DISTINCT ss.allocatedBooking.id FROM ShowSeat ss
            WHERE ss.id IN :ids
              AND ss.status = com.ticketnest.entity.ShowSeatStatus.HELD
              AND ss.holdExpiresAt <= :now
            """)
    List<UUID> findExpiredBookingIdsForSeats(@Param("ids") Collection<UUID> ids, @Param("now") Instant now);
}
