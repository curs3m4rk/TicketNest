package com.ticketnest.booking;

import com.ticketnest.booking.dto.BookingCreateRequest;
import com.ticketnest.common.ConflictException;
import com.ticketnest.entity.*;
import com.ticketnest.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {
    private static final Instant NOW = Instant.parse("2027-01-10T10:00:00Z");
    private static final String EMAIL = "user@example.com";
    @Mock BookingRepository bookings;
    @Mock BookingSeatRepository bookingSeats;
    @Mock ShowRepository shows;
    @Mock ShowInventoryRepository inventories;
    @Mock ShowSeatRepository seats;
    @Mock UserRepository users;
    @Mock BookingExpirationService expiration;
    private BookingService service;
    private User user;
    private Show show;

    @BeforeEach
    void setUp() {
        service = new BookingService(bookings, bookingSeats, shows, inventories, seats, users,
                expiration, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(10));
        user = new User();
        user.setId(UUID.randomUUID());
        show = new Show();
        show.setId(UUID.randomUUID());
        show.setStatus("ACTIVE");
        show.setStartTime(NOW.plusSeconds(3600));
    }

    @Test
    void createHoldsSeatsUsingSnapshotPricesAndConfiguredExpiry() {
        ShowSeat first = seat("1000.25");
        ShowSeat second = seat("500.50");
        var request = request(first, second);
        prepareNewBooking();
        when(seats.findRequestedForUpdate(show.getId(), Set.copyOf(request.showSeatIds())))
                .thenReturn(List.of(first, second));

        var result = service.create(EMAIL, " checkout ", request);

        assertTrue(result.created());
        assertEquals(BookingStatus.HELD, result.response().status());
        assertEquals(new BigDecimal("1500.75"), result.response().totalAmount());
        assertEquals("INR", result.response().currency());
        assertEquals(NOW.plusSeconds(600), result.response().expiresAt());
        Booking booking = first.getAllocatedBooking();
        assertEquals("checkout", booking.getIdempotencyKey());
        assertEquals(2, booking.getSeats().size());
        assertEquals(List.of(new BigDecimal("1000.25"), new BigDecimal("500.50")),
                result.response().seats().stream().map(item -> item.unitPrice()).toList());
        for (ShowSeat seat : List.of(first, second)) {
            assertEquals(ShowSeatStatus.HELD, seat.getStatus());
            assertSame(booking, seat.getAllocatedBooking());
            assertEquals(NOW.plusSeconds(600), seat.getHoldExpiresAt());
        }
        var order = inOrder(expiration, seats);
        order.verify(expiration).expireForSeats(Set.copyOf(request.showSeatIds()), NOW);
        order.verify(seats).findRequestedForUpdate(show.getId(), Set.copyOf(request.showSeatIds()));
        verify(bookings).saveAndFlush(booking);
        verify(bookingSeats).saveAll(booking.getSeats());
        verify(seats).saveAllAndFlush(List.of(first, second));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingIdempotencyKeyBeforeRepositoryAccess(String key) {
        assertThrows(IllegalArgumentException.class, () -> service.create(EMAIL, key, request(seat("1"))));
        verifyNoInteractions(users, bookings, shows, inventories, seats, bookingSeats, expiration);
    }

    @Test
    void rejectsOverlongIdempotencyKey() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create(EMAIL, "x".repeat(256), request(seat("1"))));
        verifyNoInteractions(users, bookings);
    }

    @Test
    void rejectsNullAndDuplicateSeatIdsBeforeRepositoryAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create(EMAIL, "key", new BookingCreateRequest(show.getId(), null)));
        ShowSeat seat = seat("1");
        assertThrows(IllegalArgumentException.class, () -> service.create(EMAIL, "key", request(seat, seat)));
        verifyNoInteractions(users, bookings, seats);
    }

    @Test
    void identicalRetryAcceptsReorderedSeatsWithoutCreatingAnotherHold() {
        ShowSeat first = seat("10");
        ShowSeat second = seat("20");
        Booking existing = booking(BookingStatus.HELD);
        for (ShowSeat seat : List.of(first, second)) {
            BookingSeat item = new BookingSeat();
            item.setShowSeat(seat);
            item.setUnitPrice(seat.getPrice());
            existing.getSeats().add(item);
        }
        when(users.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
        when(bookings.findByUserIdAndIdempotencyKey(user.getId(), "key")).thenReturn(Optional.of(existing));
        var result = service.create(EMAIL, " key ", request(second, first));
        assertFalse(result.created());
        assertEquals(existing.getId(), result.response().id());
        verify(bookings, never()).saveAndFlush(any());
        verifyNoInteractions(shows, inventories, seats, bookingSeats, expiration);
    }

    @Test
    void rejectsRetryWithDifferentSeats() {
        when(users.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
        when(bookings.findByUserIdAndIdempotencyKey(user.getId(), "key"))
                .thenReturn(Optional.of(booking(BookingStatus.HELD)));
        assertThrows(ConflictException.class, () -> service.create(EMAIL, "key", request(seat("10"))));
        verifyNoInteractions(shows, inventories, seats, bookingSeats, expiration);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INACTIVE", "CANCELLED"})
    void rejectsInactiveShows(String status) {
        show.setStatus(status);
        prepareShowLookup();
        assertThrows(ConflictException.class, () -> service.create(EMAIL, "key", request(seat("1"))));
        verifyNoInteractions(inventories, seats, bookingSeats, expiration);
    }

    @Test
    void rejectsShowAtStartTime() {
        show.setStartTime(NOW);
        prepareShowLookup();
        assertThrows(ConflictException.class, () -> service.create(EMAIL, "key", request(seat("1"))));
        verifyNoInteractions(inventories, seats, bookingSeats, expiration);
    }

    @Test
    void rejectsUninitializedInventory() {
        prepareShowLookup();
        assertThrows(ConflictException.class, () -> service.create(EMAIL, "key", request(seat("1"))));
        verifyNoInteractions(seats, bookingSeats, expiration);
    }

    @Test
    void rejectsMissingOrWrongShowSeatWithoutWriting() {
        prepareNewBooking();
        assertThrows(IllegalArgumentException.class, () -> service.create(EMAIL, "key", request(seat("1"))));
        verify(bookings, never()).saveAndFlush(any());
        verifyNoInteractions(bookingSeats);
    }

    @ParameterizedTest
    @EnumSource(value = ShowSeatStatus.class, names = {"HELD", "BOOKED"})
    void unavailableSeatRejectsWholeHoldWithoutMutatingAvailableSeat(ShowSeatStatus status) {
        prepareNewBooking();
        ShowSeat available = seat("10");
        ShowSeat unavailable = seat("20");
        unavailable.setStatus(status);
        var request = request(available, unavailable);
        when(seats.findRequestedForUpdate(show.getId(), Set.copyOf(request.showSeatIds())))
                .thenReturn(List.of(available, unavailable));
        assertThrows(ConflictException.class, () -> service.create(EMAIL, "key", request));
        assertEquals(ShowSeatStatus.AVAILABLE, available.getStatus());
        assertNull(available.getAllocatedBooking());
        verify(bookings, never()).saveAndFlush(any());
        verifyNoInteractions(bookingSeats);
        verify(seats, never()).saveAllAndFlush(any());
    }

    @Test
    void getAndCancelHideAnotherUsersBooking() {
        Booking booking = booking(BookingStatus.HELD);
        User other = new User();
        other.setId(UUID.randomUUID());
        booking.setUser(other);
        prepareOwnedLookup(booking);
        assertThrows(EntityNotFoundException.class, () -> service.get(EMAIL, booking.getId()));
        assertThrows(EntityNotFoundException.class, () -> service.cancel(EMAIL, booking.getId()));
        verifyNoInteractions(expiration);
    }

    @Test
    void cancelsActiveHoldAndReleasesSeats() {
        Booking booking = booking(BookingStatus.HELD);
        prepareOwnedLookup(booking);
        assertEquals(BookingStatus.RELEASED, service.cancel(EMAIL, booking.getId()).status());
        assertEquals(NOW, booking.getUpdatedAt());
        verify(expiration).releaseSeats(booking, NOW);
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"RELEASED", "EXPIRED"})
    void cancellationIsIdempotentForReleasedAndExpiredBookings(BookingStatus status) {
        Booking booking = booking(status);
        prepareOwnedLookup(booking);
        assertEquals(status, service.cancel(EMAIL, booking.getId()).status());
        verifyNoInteractions(expiration);
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"CONFIRMED", "FAILED"})
    void rejectsCancellationOfNonCancellableStatus(BookingStatus status) {
        Booking booking = booking(status);
        prepareOwnedLookup(booking);
        assertThrows(ConflictException.class, () -> service.cancel(EMAIL, booking.getId()));
        verifyNoInteractions(expiration);
    }

    @Test
    void getExpiresHoldAtExactDeadline() {
        Booking booking = booking(BookingStatus.HELD);
        booking.setExpiresAt(NOW);
        prepareOwnedLookup(booking);
        when(expiration.expireIfDue(booking.getId(), NOW)).thenAnswer(invocation -> {
            booking.setStatus(BookingStatus.EXPIRED);
            return true;
        });
        assertEquals(BookingStatus.EXPIRED, service.get(EMAIL, booking.getId()).status());
        verify(expiration).expireIfDue(booking.getId(), NOW);
    }

    @Test
    void cancelExpiresDueHoldInsteadOfMarkingItReleased() {
        Booking booking = booking(BookingStatus.HELD);
        booking.setExpiresAt(NOW);
        prepareOwnedLookup(booking);
        when(expiration.expireIfDue(booking.getId(), NOW)).thenAnswer(invocation -> {
            booking.setStatus(BookingStatus.EXPIRED);
            return true;
        });
        assertEquals(BookingStatus.EXPIRED, service.cancel(EMAIL, booking.getId()).status());
        verify(expiration, never()).releaseSeats(any(), any());
    }

    @Test
    void listExpiresUserHoldsBeforeFetchingFilteredPage() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        var pageable = PageRequest.of(0, 5);
        Booking booking = booking(BookingStatus.EXPIRED);
        when(bookings.findByUserIdAndStatus(user.getId(), BookingStatus.EXPIRED, pageable))
                .thenReturn(new PageImpl<>(List.of(booking), pageable, 1));
        var result = service.list(EMAIL, BookingStatus.EXPIRED, pageable);
        assertEquals(booking.getId(), result.content().getFirst().id());
        assertEquals(1, result.totalElements());
        var order = inOrder(expiration, bookings);
        order.verify(expiration).expireForUser(user.getId(), NOW);
        order.verify(bookings).findByUserIdAndStatus(user.getId(), BookingStatus.EXPIRED, pageable);
        verify(bookings, never()).findByUserId(any(), any());
    }

    private void prepareShowLookup() {
        when(users.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
        when(shows.findById(show.getId())).thenReturn(Optional.of(show));
    }

    private void prepareNewBooking() {
        prepareShowLookup();
        ShowInventory inventory = new ShowInventory();
        inventory.setCurrency("INR");
        when(inventories.findById(show.getId())).thenReturn(Optional.of(inventory));
    }

    private void prepareOwnedLookup(Booking booking) {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(bookings.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));
    }

    private Booking booking(BookingStatus status) {
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setUser(user);
        booking.setShow(show);
        booking.setStatus(status);
        booking.setExpiresAt(NOW.plusSeconds(600));
        return booking;
    }

    private ShowSeat seat(String price) {
        ShowSeat seat = new ShowSeat();
        seat.setId(UUID.randomUUID());
        Seat source = new Seat();
        source.setId(UUID.randomUUID());
        seat.setSourceSeat(source);
        seat.setPrice(new BigDecimal(price));
        seat.setStatus(ShowSeatStatus.AVAILABLE);
        return seat;
    }

    private BookingCreateRequest request(ShowSeat... seats) {
        return new BookingCreateRequest(show.getId(), Arrays.stream(seats).map(ShowSeat::getId).toList());
    }
}
