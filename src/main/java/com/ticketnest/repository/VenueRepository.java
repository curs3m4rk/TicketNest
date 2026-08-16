package com.ticketnest.repository;

import com.ticketnest.entity.Venue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Venue entity.
 * Provides custom query to fetch distinct seat tiers for a venue.
 */
public interface VenueRepository extends JpaRepository<Venue, UUID> {
    /**
     * Returns distinct seat tiers (e.g., VIP, PREMIUM, STANDARD) for a venue.
     * Used in catalog responses to show available pricing tiers.
     */
    @Query("""
        SELECT DISTINCT s.tier
        FROM Seat s
        WHERE s.venue.id = :venueId
        ORDER BY s.tier
        """)
    List<String> findSeatTiersByVenueId(@Param("venueId") UUID venueId);
}
