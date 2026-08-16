package com.ticketnest.venue;

import com.ticketnest.venue.dto.VenueRequest;
import com.ticketnest.venue.dto.VenueResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for Venue CRUD.
 * Endpoints: GET/POST /api/venues, GET/PUT/DELETE /api/venues/{id}
 * Returns VenueResponse with seat tiers for catalog display.
 */
@RestController
@RequestMapping("/api/venues")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    /** Lists all venues with seat tiers. */
    @GetMapping
    public List<VenueResponse> getVenues() {
        return venueService.getAllVenues();
    }

    /** Gets a single venue by ID with seat tiers. */
    @GetMapping("/{id}")
    public VenueResponse getVenue(@PathVariable UUID id) {
        return venueService.getVenue(id);
    }

    /** Creates a new venue. Returns 201 with created venue. */
    @PostMapping
    public ResponseEntity<VenueResponse> createVenue(@Valid @RequestBody VenueRequest request) {
        VenueResponse response = venueService.createVenue(request);
        return ResponseEntity.status(201).body(response);
    }

    /** Updates an existing venue. */
    @PutMapping("/{id}")
    public VenueResponse updateVenue(@PathVariable UUID id, @Valid @RequestBody VenueRequest request) {
        return venueService.updateVenue(id, request);
    }

    /** Deletes a venue. Returns 204 no content. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVenue(@PathVariable UUID id) {
        venueService.deleteVenue(id);
        return ResponseEntity.noContent().build();
    }
}