package com.ticketnest.repository;

import com.ticketnest.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
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
}
