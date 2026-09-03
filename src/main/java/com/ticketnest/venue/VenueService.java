package com.ticketnest.venue;

import com.ticketnest.common.ConflictException;
import com.ticketnest.entity.Seat;
import com.ticketnest.entity.Venue;
import com.ticketnest.repository.SeatRepository;
import com.ticketnest.repository.VenueRepository;
import com.ticketnest.venue.dto.SeatBatchCreateRequest;
import com.ticketnest.venue.dto.SeatBatchCreateResponse;
import com.ticketnest.venue.dto.SeatRangeRequest;
import com.ticketnest.venue.dto.SeatResponse;
import com.ticketnest.venue.dto.VenueRequest;
import com.ticketnest.venue.dto.VenueResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Service layer for Venue CRUD operations.
 * Maps between JPA entities and API DTOs, enriches with seat tiers from Seat table.
 */
@Service
public class VenueService {

    private static final int MAX_SEATS_PER_REQUEST = 10_000;
    private static final Comparator<String> NATURAL_SEAT_NUMBER_COMPARATOR = VenueService::compareSeatNumbers;

    private final VenueRepository venueRepository;
    private final SeatRepository seatRepository;

    public VenueService(VenueRepository venueRepository, SeatRepository seatRepository) {
        this.venueRepository = venueRepository;
        this.seatRepository = seatRepository;
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

    public List<SeatResponse> getSeats(UUID venueId) {
        if (!venueRepository.existsById(venueId)) {
            throw new EntityNotFoundException("Venue with id " + venueId + " not found");
        }
        return seatRepository.findByVenueId(venueId)
                .stream()
                .sorted(Comparator.comparing(Seat::getRow, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Seat::getRow)
                        .thenComparing(Seat::getNumber, NATURAL_SEAT_NUMBER_COMPARATOR))
                .map(this::toSeatResponse)
                .toList();
    }

    @Transactional
    public SeatBatchCreateResponse createSeats(UUID venueId, SeatBatchCreateRequest request) {
        Venue venue = venueRepository.findById(venueId)
                .orElseThrow(() -> new EntityNotFoundException("Venue with id " + venueId + " not found"));

        List<NormalizedSeatRange> ranges = normalizeAndValidateRanges(request.ranges());
        Set<SeatKey> requestedSeats = expandSeatKeys(ranges);
        Set<SeatKey> existingSeats = seatRepository.findByVenueId(venueId).stream()
                .map(seat -> new SeatKey(seat.getRow(), seat.getNumber()))
                .collect(java.util.stream.Collectors.toSet());

        requestedSeats.stream()
                .filter(existingSeats::contains)
                .findFirst()
                .ifPresent(key -> {
                    throw new ConflictException("Seat " + key.row() + "-" + key.number()
                            + " already exists for venue " + venueId);
                });

        Instant createdAt = Instant.now();
        List<Seat> seats = new ArrayList<>(requestedSeats.size());
        for (NormalizedSeatRange range : ranges) {
            for (long number = range.startNumber(); number <= range.endNumber(); number++) {
                Seat seat = new Seat();
                seat.setVenue(venue);
                seat.setRow(range.row());
                seat.setNumber(Long.toString(number));
                seat.setTier(range.tier());
                seat.setCreatedAt(createdAt);
                seats.add(seat);
            }
        }

        seatRepository.saveAllAndFlush(seats);
        return new SeatBatchCreateResponse(venueId, seats.size());
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

    private SeatResponse toSeatResponse(Seat seat) {
        return new SeatResponse(seat.getId(), seat.getRow(), seat.getNumber(), seat.getTier());
    }

    private List<NormalizedSeatRange> normalizeAndValidateRanges(List<SeatRangeRequest> requests) {
        List<NormalizedSeatRange> ranges = new ArrayList<>(requests.size());
        long totalSeats = 0;

        for (SeatRangeRequest request : requests) {
            if (request.startNumber() > request.endNumber()) {
                throw new IllegalArgumentException("Start number must not be greater than end number");
            }

            long rangeSize = (long) request.endNumber() - request.startNumber() + 1;
            totalSeats += rangeSize;
            if (totalSeats > MAX_SEATS_PER_REQUEST) {
                throw new IllegalArgumentException("A maximum of " + MAX_SEATS_PER_REQUEST
                        + " seats can be created in one request");
            }

            ranges.add(new NormalizedSeatRange(
                    request.row().trim().toUpperCase(Locale.ROOT),
                    request.startNumber(),
                    request.endNumber(),
                    request.tier().trim().toUpperCase(Locale.ROOT)
            ));
        }
        return ranges;
    }

    private Set<SeatKey> expandSeatKeys(List<NormalizedSeatRange> ranges) {
        Set<SeatKey> keys = new HashSet<>();
        for (NormalizedSeatRange range : ranges) {
            for (long number = range.startNumber(); number <= range.endNumber(); number++) {
                SeatKey key = new SeatKey(range.row(), Long.toString(number));
                if (!keys.add(key)) {
                    throw new IllegalArgumentException("Seat " + key.row() + "-" + key.number()
                            + " is included more than once in the request");
                }
            }
        }
        return keys;
    }

    private static int compareSeatNumbers(String left, String right) {
        boolean leftNumeric = left.matches("\\d+");
        boolean rightNumeric = right.matches("\\d+");
        if (leftNumeric && rightNumeric) {
            int numericComparison = new BigInteger(left).compareTo(new BigInteger(right));
            return numericComparison != 0 ? numericComparison : left.compareTo(right);
        }
        if (leftNumeric != rightNumeric) {
            return leftNumeric ? -1 : 1;
        }
        int caseInsensitiveComparison = left.compareToIgnoreCase(right);
        return caseInsensitiveComparison != 0 ? caseInsensitiveComparison : left.compareTo(right);
    }

    private record NormalizedSeatRange(String row, int startNumber, int endNumber, String tier) {}

    private record SeatKey(String row, String number) {}
}
