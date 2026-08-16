package com.ticketnest.repository;

import com.ticketnest.entity.Show;
import com.ticketnest.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Show entity.
 * Provides custom queries for catalog: find by city+date, fetch with venue, and seat tiers.
 */
public interface ShowRepository extends JpaRepository<Show, UUID> {
    /**
     * Finds shows in a city within a date range (inclusive start, exclusive end).
     * Used for city-based event discovery.
     */
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

    /**
     * Fetches all shows with venue eagerly loaded (JOIN FETCH).
     * Avoids N+1 when listing shows in catalog.
     */
    @Query("""
        SELECT s
        FROM Show s
        JOIN FETCH s.venue
        """)
    List<Show> findAllWithVenue();

    /**
     * Fetches a single show with venue eagerly loaded.
     * Avoids N+1 when retrieving show details.
     */
    @Query("""
        SELECT s
        FROM Show s
        JOIN FETCH s.venue
        WHERE s.id = :id
        """)
    Optional<Show> findByIdWithVenue(@Param("id") UUID id);

    /**
     * Returns distinct seat tiers for a venue (via its shows).
     * Used to populate seatTiers in ShowResponse.VenueSummary.
     */
    @Query("""
        SELECT DISTINCT s.tier
        FROM Seat s
        WHERE s.venue.id = :venueId
        ORDER BY s.tier
        """)
    List<String> findSeatTiersByVenueId(@Param("venueId") UUID venueId);

    /**
     * Spring Data derived query: finds all shows for a given venue.
     * Used when deleting a venue to check for dependent shows.
     */
    List<Show> findByVenue(Venue venue);
}
