package com.ticketnest.show;

import com.ticketnest.common.dto.PageResponse;
import com.ticketnest.entity.Show;
import com.ticketnest.entity.Venue;
import com.ticketnest.repository.ShowRepository;
import com.ticketnest.repository.VenueRepository;
import com.ticketnest.show.dto.ShowRequest;
import com.ticketnest.show.dto.ShowResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service layer for Show CRUD operations.
 * Validates venue exists, maps entities to DTOs, enriches with seat tiers from venue.
 */
@Service
public class ShowService {

    private final ShowRepository showRepository;
    private final VenueRepository venueRepository;

    public ShowService(ShowRepository showRepository, VenueRepository venueRepository) {
        this.showRepository = showRepository;
        this.venueRepository = venueRepository;
    }

    /** Returns all shows with venue and seat tiers (uses JOIN FETCH to avoid N+1). */
    public List<ShowResponse> getAllShows() {
        return showRepository.findAllWithVenue()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Returns a paginated list of all shows with venue and seat tiers.
     * Maps Page<Show> to PageResponse<ShowResponse> for API layer.
     */
    public PageResponse<ShowResponse> getAllShows(Pageable pageable) {
        Page<Show> page = showRepository.findAllWithVenue(pageable);
        List<ShowResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .toList();
        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.isEmpty()
        );
    }

    /** Returns a single show by ID with venue and seat tiers. Throws if not found. */
    public ShowResponse getShow(UUID id) {
        return showRepository.findByIdWithVenue(id)
                .map(this::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("Show with id " + id + " not found"));
    }

    /** Creates a show linked to an existing venue. Returns created show with venue + seat tiers. */
    public ShowResponse createShow(ShowRequest request) {
        Venue venue = venueRepository.findById(request.venueId())
                .orElseThrow(() -> new EntityNotFoundException("Venue with id " + request.venueId() + " not found"));

        Show show = new Show();
        show.setVenue(venue);
        show.setTitle(request.title());
        show.setGenre(request.genre());
        show.setStartTime(request.startTime());
        show.setStatus(request.status());
        show.setCreatedAt(Instant.now());

        Show saved = showRepository.save(show);
        return toResponse(saved);
    }

    /** Updates show fields (including venue reassignment). Returns updated show with venue + seat tiers. */
    public ShowResponse updateShow(UUID id, ShowRequest request) {
        Show show = showRepository.findByIdWithVenue(id)
                .orElseThrow(() -> new EntityNotFoundException("Show with id " + id + " not found"));

        Venue venue = venueRepository.findById(request.venueId())
                .orElseThrow(() -> new EntityNotFoundException("Venue with id " + request.venueId() + " not found"));

        show.setVenue(venue);
        show.setTitle(request.title());
        show.setGenre(request.genre());
        show.setStartTime(request.startTime());
        show.setStatus(request.status());
        show.setUpdatedAt(Instant.now());

        showRepository.save(show);
        // Reload with venue eagerly fetched to avoid LazyInitializationException on new venue
        return showRepository.findByIdWithVenue(id)
                .map(this::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("Show with id " + id + " not found after update"));
    }

    /** Deletes show if it exists. Throws if not found. */
    public void deleteShow(UUID id) {
        if (!showRepository.existsById(id)) {
            throw new EntityNotFoundException("Show with id " + id + " not found");
        }
        showRepository.deleteById(id);
    }

    /** Maps Show entity to ShowResponse, enriching venue summary with seat tiers. */
    private ShowResponse toResponse(Show show) {
        List<String> seatTiers = showRepository.findSeatTiersByVenueId(show.getVenue().getId());
        return new ShowResponse(
                show.getId(),
                show.getTitle(),
                show.getGenre(),
                show.getStartTime(),
                show.getStatus(),
                new ShowResponse.VenueSummary(
                        show.getVenue().getId(),
                        show.getVenue().getName(),
                        show.getVenue().getCity(),
                        show.getVenue().getAddress(),
                        seatTiers
                )
        );
    }
}