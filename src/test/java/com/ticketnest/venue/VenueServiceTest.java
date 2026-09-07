package com.ticketnest.venue;

import com.ticketnest.common.ConflictException;
import com.ticketnest.entity.Seat;
import com.ticketnest.entity.Venue;
import com.ticketnest.repository.*;
import com.ticketnest.venue.dto.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VenueServiceTest {
    @Mock VenueRepository venues;
    @Mock SeatRepository seats;
    @InjectMocks VenueService service;
    @Captor ArgumentCaptor<List<Seat>> savedSeats;
    private final UUID venueId = UUID.randomUUID();

    @Test
    void createsInclusiveRangesWithNormalizedRowAndTier() {
        prepareVenue();
        service.createSeats(venueId, request(new SeatRangeRequest(" a ", 1, 3, " vip ")));
        verify(seats).saveAllAndFlush(savedSeats.capture());
        assertEquals(List.of("1", "2", "3"), savedSeats.getValue().stream().map(Seat::getNumber).toList());
        for (Seat seat : savedSeats.getValue()) {
            assertEquals("A", seat.getRow());
            assertEquals("VIP", seat.getTier());
            assertEquals(venueId, seat.getVenue().getId());
        }
    }

    @Test
    void rejectsOverlappingNormalizedRangesBeforeWriting() {
        prepareVenue();
        assertThrows(IllegalArgumentException.class, () -> service.createSeats(venueId, request(
                new SeatRangeRequest("a", 1, 2, "VIP"), new SeatRangeRequest(" A ", 2, 3, "STANDARD"))));
        verifyNoInteractions(seats);
    }

    @Test
    void rejectsReversedAndOversizedRanges() {
        prepareVenue();
        for (SeatRangeRequest range : List.of(new SeatRangeRequest("A", 3, 1, "VIP"),
                new SeatRangeRequest("A", 1, 10001, "VIP"))) {
            assertThrows(IllegalArgumentException.class, () -> service.createSeats(venueId, request(range)));
        }
        verifyNoInteractions(seats);
    }

    @Test
    void rejectsCombinedRangesAboveBatchLimit() {
        prepareVenue();
        assertThrows(IllegalArgumentException.class, () -> service.createSeats(venueId, request(
                new SeatRangeRequest("A", 1, 6000, "VIP"), new SeatRangeRequest("B", 1, 4001, "VIP"))));
        verifyNoInteractions(seats);
    }

    @Test
    void existingSeatRejectsEntireBatch() {
        prepareVenue();
        when(seats.findByVenueId(venueId)).thenReturn(List.of(seat("A", "2")));
        assertThrows(ConflictException.class, () -> service.createSeats(venueId,
                request(new SeatRangeRequest("A", 1, 3, "VIP"))));
        verify(seats, never()).saveAllAndFlush(any());
    }

    @Test
    void seatListingSortsRowsAndNumbersNaturally() {
        when(venues.existsById(venueId)).thenReturn(true);
        when(seats.findByVenueId(venueId)).thenReturn(List.of(
                seat("B", "1"), seat("A", "10"), seat("A", "2"), seat("A", "1")));
        var result = service.getSeats(venueId);
        assertEquals(List.of("A1", "A2", "A10", "B1"),
                result.stream().map(seat -> seat.row() + seat.number()).toList());
    }

    @Test
    void missingVenueCannotHaveSeatsCreatedOrListed() {
        assertThrows(EntityNotFoundException.class, () -> service.createSeats(venueId,
                request(new SeatRangeRequest("A", 1, 2, "VIP"))));
        assertThrows(EntityNotFoundException.class, () -> service.getSeats(venueId));
        verifyNoInteractions(seats);
    }

    private void prepareVenue() {
        Venue venue = new Venue();
        venue.setId(venueId);
        when(venues.findById(venueId)).thenReturn(Optional.of(venue));
    }

    private SeatBatchCreateRequest request(SeatRangeRequest... ranges) {
        return new SeatBatchCreateRequest(List.of(ranges));
    }

    private Seat seat(String row, String number) {
        Seat seat = new Seat();
        seat.setRow(row);
        seat.setNumber(number);
        return seat;
    }
}
