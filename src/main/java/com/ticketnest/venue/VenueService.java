package com.ticketnest.venue;

import com.ticketnest.entity.Venue;
import com.ticketnest.repository.VenueRepository;
import com.ticketnest.venue.dto.VenueRequest;
import com.ticketnest.venue.dto.VenueResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service layer for Venue CRUD operations.
 * Maps between JPA entities and API DTOs, enriches with seat tiers from Seat table.
 */
@Service
public class VenueService {

    private final VenueRepository venueRepository;

    public VenueService(VenueRepository venueRepository) {
        this.venueRepository = venueRepository;
    }

    /** Returns all venues with their seat tiers. */
    public List<VenueResponse> getAllVenues() {
        return venueRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** Returns a single venue by ID with seat tiers. Throws if not found. */
    public VenueResponse getVenue(UUID id) {
        return venueRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("Venue with id " + id + " not found"));
    }

    /** Creates a new venue (active by default) and returns it with seat tiers. */
    public VenueResponse createVenue(VenueRequest request) {
        Venue venue = new Venue();
        venue.setName(request.name());
        venue.setCity(request.city());
        venue.setAddress(request.address());
        venue.setActive(true);
        venue.setCreatedAt(Instant.now());
        Venue saved = venueRepository.save(venue);
        return toResponse(saved);
    }

    /** Updates venue fields and returns updated venue with seat tiers. */
    public VenueResponse updateVenue(UUID id, VenueRequest request) {
        Venue venue = venueRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Venue with id " + id + " not found"));
        venue.setName(request.name());
        venue.setCity(request.city());
        venue.setAddress(request.address());
        venue.setUpdatedAt(Instant.now());
        Venue saved = venueRepository.save(venue);
        return toResponse(saved);
    }

    /** Deletes venue if it exists. Throws if not found. */
    public void deleteVenue(UUID id) {
        if (!venueRepository.existsById(id)) {
            throw new EntityNotFoundException("Venue with id " + id + " not found");
        }
        venueRepository.deleteById(id);
    }

    /** Maps Venue entity to VenueResponse, enriching with seat tiers from Seat table. */
    private VenueResponse toResponse(Venue venue) {
        List<String> seatTiers = venueRepository.findSeatTiersByVenueId(venue.getId());
        return new VenueResponse(
                venue.getId(),
                venue.getName(),
                venue.getCity(),
                venue.getAddress(),
                venue.isActive(),
                seatTiers
        );
    }
}