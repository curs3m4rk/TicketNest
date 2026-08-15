package com.ticketnest.repository;

import com.ticketnest.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShowRepository extends JpaRepository<Show, UUID> {
    @Query("""
        SELECT s
        FROM Show s
        JOIN s.venue v
        WHERE v.city = :city
          AND s.startTime >= :startTime
          AND s.startTime < :endTime
        ORDER BY s.startTime
        """)
    List<Show> findShowsByCityAndDate(
            @Param("city") String city,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );

    @Query("""
        SELECT s
        FROM Show s
        JOIN FETCH s.venue
    """)
    List<Show> findAllWithVenue();

    @Query("""
        SELECT s
        FROM Show s
        JOIN FETCH s.venue
        WHERE s.id = :id
    """)
    Optional<Show> findByIdWithVenue(@Param("id") UUID id);
}
