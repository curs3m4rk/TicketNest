package com.ticketnest.booking;

import com.ticketnest.booking.dto.BookingCreateRequest;
import com.ticketnest.booking.dto.BookingResponse;
import com.ticketnest.common.ConflictException;
import com.ticketnest.common.dto.PageResponse;
import com.ticketnest.entity.*;
import com.ticketnest.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class BookingService {
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final ShowRepository showRepository;
    private final ShowInventoryRepository inventoryRepository;
    private final ShowSeatRepository showSeatRepository;
    private final UserRepository userRepository;
    private final BookingExpirationService expirationService;
    private final Clock clock;
    private final Duration holdDuration;

    public BookingService(BookingRepository bookingRepository, BookingSeatRepository bookingSeatRepository,
                          ShowRepository showRepository, ShowInventoryRepository inventoryRepository,
                          ShowSeatRepository showSeatRepository, UserRepository userRepository,
                          BookingExpirationService expirationService, Clock clock,
                          @Value("${app.booking.hold-duration:PT10M}") Duration holdDuration) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.showRepository = showRepository;
        this.inventoryRepository = inventoryRepository;
        this.showSeatRepository = showSeatRepository;
        this.userRepository = userRepository;
        this.expirationService = expirationService;
        this.clock = clock;
        this.holdDuration = holdDuration;
    }

    @Transactional
    public CreationResult create(String email, String idempotencyKey, BookingCreateRequest request) {
        validateIdempotencyKey(idempotencyKey);
        if (request.showSeatIds() == null) {
            throw new IllegalArgumentException("Show seat IDs are required");
        }
        Set<UUID> requestedIds = new HashSet<>(request.showSeatIds());
        if (requestedIds.size() != request.showSeatIds().size()) {
            throw new IllegalArgumentException("Show seat IDs must be distinct");
        }

        User user = userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new EntityNotFoundException("Authenticated user not found"));
        var existing = bookingRepository.findByUserIdAndIdempotencyKey(user.getId(), idempotencyKey.trim());
        if (existing.isPresent()) {
            Booking booking = existing.get();
            Set<UUID> existingSeatIds = booking.getSeats().stream()
                    .map(item -> item.getShowSeat().getId()).collect(java.util.stream.Collectors.toSet());
            if (!booking.getShow().getId().equals(request.showId()) || !existingSeatIds.equals(requestedIds)) {
                throw new ConflictException("Idempotency-Key was already used for a different booking request");
            }
            return new CreationResult(toResponse(booking), false);
        }

        Show show = showRepository.findById(request.showId())
                .orElseThrow(() -> new EntityNotFoundException("Show with id " + request.showId() + " not found"));
        Instant now = clock.instant();
        if (!"ACTIVE".equalsIgnoreCase(show.getStatus()) || !show.getStartTime().isAfter(now)) {
            throw new ConflictException("Only active future shows can be booked");
        }
        ShowInventory inventory = inventoryRepository.findById(request.showId())
                .orElseThrow(() -> new ConflictException("Inventory is not initialized for show " + request.showId()));

        expirationService.expireForSeats(requestedIds, now);
        List<ShowSeat> seats = showSeatRepository.findRequestedForUpdate(request.showId(), requestedIds);
        if (seats.size() != requestedIds.size()) {
            throw new IllegalArgumentException("Every show seat must exist and belong to the requested show");
        }
        if (seats.stream().anyMatch(seat -> seat.getStatus() != ShowSeatStatus.AVAILABLE)) {
            throw new ConflictException("One or more requested seats are unavailable");
        }

        BigDecimal total = seats.stream().map(ShowSeat::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        Instant expiresAt = now.plus(holdDuration);
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setShow(show);
        booking.setStatus(BookingStatus.HELD);
        booking.setIdempotencyKey(idempotencyKey.trim());
        booking.setCreatedAt(now);
        booking.setExpiresAt(expiresAt);
        booking.setTotalAmount(total);
        booking.setCurrency(inventory.getCurrency());
        bookingRepository.saveAndFlush(booking);

        for (ShowSeat seat : seats) {
            seat.setStatus(ShowSeatStatus.HELD);
            seat.setAllocatedBooking(booking);
            seat.setHoldExpiresAt(expiresAt);
            seat.setUpdatedAt(now);

            BookingSeat item = new BookingSeat();
            item.setBooking(booking);
            item.setShowSeat(seat);
            item.setUnitPrice(seat.getPrice());
            item.setCreatedAt(now);
            booking.getSeats().add(item);
        }
        bookingSeatRepository.saveAll(booking.getSeats());
        showSeatRepository.saveAllAndFlush(seats);
        return new CreationResult(toResponse(booking), true);
    }

    @Transactional
    public BookingResponse get(String email, UUID bookingId) {
        User user = currentUser(email);
        Booking booking = ownedBooking(user, bookingId, true);
        if (booking.getStatus() == BookingStatus.HELD && !booking.getExpiresAt().isAfter(clock.instant())) {
            expirationService.expireIfDue(bookingId, clock.instant());
        }
        return toResponse(booking);
    }

    @Transactional
    public PageResponse<BookingResponse> list(String email, BookingStatus status, Pageable pageable) {
        User user = currentUser(email);
        Instant now = clock.instant();
        expirationService.expireForUser(user.getId(), now);
        Page<Booking> page = status == null
                ? bookingRepository.findByUserId(user.getId(), pageable)
                : bookingRepository.findByUserIdAndStatus(user.getId(), status, pageable);
        List<BookingResponse> content = page.getContent().stream().map(this::toResponse).toList();
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast(), page.isEmpty());
    }

    @Transactional
    public BookingResponse cancel(String email, UUID bookingId) {
        User user = currentUser(email);
        Booking booking = ownedBooking(user, bookingId, true);
        Instant now = clock.instant();
        if (booking.getStatus() == BookingStatus.HELD && !booking.getExpiresAt().isAfter(now)) {
            expirationService.expireIfDue(bookingId, now);
            return toResponse(booking);
        }
        if (booking.getStatus() == BookingStatus.RELEASED || booking.getStatus() == BookingStatus.EXPIRED) {
            return toResponse(booking);
        }
        if (booking.getStatus() != BookingStatus.HELD) {
            throw new ConflictException("Booking in status " + booking.getStatus() + " cannot be cancelled");
        }
        expirationService.releaseSeats(booking, now);
        booking.setStatus(BookingStatus.RELEASED);
        booking.setUpdatedAt(now);
        return toResponse(booking);
    }

    private User currentUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Authenticated user not found"));
    }

    private Booking ownedBooking(User user, UUID id, boolean lock) {
        Booking booking = (lock ? bookingRepository.findByIdForUpdate(id) : bookingRepository.findById(id))
                .orElseThrow(() -> new EntityNotFoundException("Booking with id " + id + " not found"));
        if (!booking.getUser().getId().equals(user.getId())) {
            throw new EntityNotFoundException("Booking with id " + id + " not found");
        }
        return booking;
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.trim().length() > 255) {
            throw new IllegalArgumentException("Idempotency-Key must contain between 1 and 255 characters");
        }
    }

    private BookingResponse toResponse(Booking booking) {
        List<BookingResponse.Seat> seats = booking.getSeats().stream().map(item -> {
            ShowSeat seat = item.getShowSeat();
            return new BookingResponse.Seat(seat.getId(), seat.getSourceSeat().getId(), seat.getRow(), seat.getNumber(),
                    seat.getTier(), item.getUnitPrice());
        }).toList();
        return new BookingResponse(booking.getId(), booking.getShow().getId(), booking.getStatus(),
                booking.getTotalAmount(), booking.getCurrency(), booking.getCreatedAt(), booking.getExpiresAt(), seats);
    }

    public record CreationResult(BookingResponse response, boolean created) {
    }
}
