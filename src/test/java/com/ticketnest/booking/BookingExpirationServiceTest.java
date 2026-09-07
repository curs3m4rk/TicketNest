package com.ticketnest.booking;

import com.ticketnest.entity.*;
import com.ticketnest.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingExpirationServiceTest {
    private static final Instant NOW = Instant.parse("2027-01-10T10:00:00Z");
    @Mock BookingRepository bookings;
    @Mock ShowSeatRepository seats;
    private BookingExpirationService service;

    @BeforeEach
    void setUp() {
        service = new BookingExpirationService(bookings, seats, Clock.fixed(NOW, ZoneOffset.UTC), 25);
    }

    @Test
    void expiresAtExactDeadlineAndClearsSeatAllocation() {
        Booking booking = booking(BookingStatus.HELD, NOW);
        ShowSeat seat = new ShowSeat();
        seat.setStatus(ShowSeatStatus.HELD);
        seat.setAllocatedBooking(booking);
        seat.setHoldExpiresAt(NOW);
        when(bookings.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));
        when(seats.findAllocatedForUpdate(booking.getId())).thenReturn(List.of(seat));
        assertTrue(service.expireIfDue(booking.getId(), NOW));
        assertEquals(BookingStatus.EXPIRED, booking.getStatus());
        assertEquals(NOW, booking.getUpdatedAt());
        assertEquals(ShowSeatStatus.AVAILABLE, seat.getStatus());
        assertNull(seat.getAllocatedBooking());
        assertNull(seat.getHoldExpiresAt());
        assertEquals(NOW, seat.getUpdatedAt());
    }

    @Test
    void leavesHoldBeforeDeadlineUntouched() {
        Booking booking = booking(BookingStatus.HELD, NOW.plusNanos(1));
        when(bookings.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));
        assertFalse(service.expireIfDue(booking.getId(), NOW));
        assertEquals(BookingStatus.HELD, booking.getStatus());
        verifyNoInteractions(seats);
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"CONFIRMED", "RELEASED", "FAILED", "EXPIRED"})
    void doesNotExpireOtherStatuses(BookingStatus status) {
        Booking booking = booking(status, NOW.minusSeconds(1));
        when(bookings.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));
        assertFalse(service.expireIfDue(booking.getId(), NOW));
        assertEquals(status, booking.getStatus());
        verifyNoInteractions(seats);
    }

    @Test
    void missingBookingAndEmptySeatSelectionAreNoOps() {
        assertFalse(service.expireIfDue(UUID.randomUUID(), NOW));
        service.expireForSeats(List.of(), NOW);
        verifyNoInteractions(seats);
    }

    @Test
    void releasePreservesBookedSeats() {
        Booking booking = booking(BookingStatus.CONFIRMED, NOW);
        ShowSeat seat = new ShowSeat();
        seat.setStatus(ShowSeatStatus.BOOKED);
        seat.setAllocatedBooking(booking);
        when(seats.findAllocatedForUpdate(booking.getId())).thenReturn(List.of(seat));
        service.releaseSeats(booking, NOW);
        assertEquals(ShowSeatStatus.BOOKED, seat.getStatus());
        assertSame(booking, seat.getAllocatedBooking());
    }

    @Test
    void cleanupUsesConfiguredBatchSizeAndRechecksEligibility() {
        Booking due = booking(BookingStatus.HELD, NOW);
        Booking confirmed = booking(BookingStatus.CONFIRMED, NOW);
        Booking future = booking(BookingStatus.HELD, NOW.plusSeconds(1));
        List<UUID> ids = List.of(due.getId(), confirmed.getId(), future.getId());
        when(bookings.findExpiredIdsForUpdate(NOW, 25)).thenReturn(ids);
        when(bookings.findAllByIdForUpdate(ids)).thenReturn(List.of(due, confirmed, future));
        service.cleanupExpiredHolds();
        assertEquals(BookingStatus.EXPIRED, due.getStatus());
        assertEquals(BookingStatus.CONFIRMED, confirmed.getStatus());
        assertEquals(BookingStatus.HELD, future.getStatus());
        verify(seats).findAllocatedForUpdate(due.getId());
        verifyNoMoreInteractions(seats);
    }

    @Test
    void emptyCleanupBatchDoesNotFetchBookings() {
        service.cleanupExpiredHolds();
        verify(bookings).findExpiredIdsForUpdate(NOW, 25);
        verifyNoMoreInteractions(bookings);
        verifyNoInteractions(seats);
    }

    private Booking booking(BookingStatus status, Instant expiresAt) {
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setStatus(status);
        booking.setExpiresAt(expiresAt);
        return booking;
    }
}
