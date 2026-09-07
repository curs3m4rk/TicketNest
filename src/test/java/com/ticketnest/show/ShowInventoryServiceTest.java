package com.ticketnest.show;

import com.ticketnest.booking.BookingExpirationService;
import com.ticketnest.common.ConflictException;
import com.ticketnest.entity.*;
import com.ticketnest.repository.*;
import com.ticketnest.show.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShowInventoryServiceTest {
    private static final Instant NOW = Instant.parse("2027-01-10T10:00:00Z");
    @Mock ShowRepository shows;
    @Mock SeatRepository seats;
    @Mock ShowInventoryRepository inventories;
    @Mock ShowSeatRepository snapshots;
    @Mock BookingExpirationService expiration;
    @Captor ArgumentCaptor<List<ShowSeat>> savedSeats;
    private ShowInventoryService service;
    private Show show;
    private Seat source;

    @BeforeEach
    void setUp() {
        service = new ShowInventoryService(shows, seats, inventories, snapshots, expiration,
                Clock.fixed(NOW, ZoneOffset.UTC));
        Venue venue = new Venue();
        venue.setId(UUID.randomUUID());
        show = new Show();
        show.setId(UUID.randomUUID());
        show.setVenue(venue);
        show.setStartTime(NOW.plusSeconds(3600));
        source = new Seat();
        source.setId(UUID.randomUUID());
        source.setRow("A");
        source.setNumber("1");
        source.setTier(" vip ");
    }

    @Test
    void initializationNormalizesCurrencyAndCopiesLayoutAndPrices() {
        prepareSeats();
        var result = service.initialize(show.getId(), request("inr", price(" vip ", "100.5")));
        assertEquals("INR", result.currency());
        assertEquals(1, result.seatCount());
        verify(snapshots).saveAllAndFlush(savedSeats.capture());
        ShowSeat snapshot = savedSeats.getValue().getFirst();
        assertSame(source, snapshot.getSourceSeat());
        assertEquals("VIP", snapshot.getTier());
        assertEquals(new BigDecimal("100.50"), snapshot.getPrice());
        assertEquals(ShowSeatStatus.AVAILABLE, snapshot.getStatus());
        assertEquals(NOW, snapshot.getCreatedAt());
        source.setRow("B");
        source.setNumber("9");
        source.setTier("STANDARD");
        assertEquals("A", snapshot.getRow());
        assertEquals("1", snapshot.getNumber());
        assertEquals("VIP", snapshot.getTier());
        verify(inventories).saveAndFlush(snapshot.getInventory());
    }

    @Test
    void rejectsShowThatHasJustStarted() {
        show.setStartTime(NOW);
        prepareShow();
        assertThrows(ConflictException.class, () -> service.initialize(show.getId(), request("INR", price("VIP", "1"))));
        verifyNoInteractions(inventories, seats, snapshots);
    }

    @Test
    void rejectsRepeatedInitialization() {
        prepareShow();
        when(inventories.existsById(show.getId())).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.initialize(show.getId(), request("INR", price("VIP", "1"))));
        verify(inventories, never()).saveAndFlush(any());
        verifyNoInteractions(seats, snapshots);
    }

    @Test
    void rejectsUnknownCurrency() {
        prepareShow();
        assertThrows(IllegalArgumentException.class, () -> service.initialize(show.getId(), request("ZZZ", price("VIP", "1"))));
        verifyNoInteractions(seats, snapshots);
    }

    @Test
    void rejectsVenueWithoutSeats() {
        prepareShow();
        assertThrows(ConflictException.class, () -> service.initialize(show.getId(), request("INR", price("VIP", "1"))));
        verify(inventories, never()).saveAndFlush(any());
        verifyNoInteractions(snapshots);
    }

    @Test
    void rejectsMissingUnknownDuplicateAndOverpreciseTierPrices() {
        prepareSeats();
        for (ShowInventoryRequest request : List.of(
                request("INR"),
                request("INR", price("VIP", "1"), price("STANDARD", "2")),
                request("INR", price("VIP", "1"), price(" vip ", "2")),
                request("INR", price("VIP", "1.001")))) {
            assertThrows(IllegalArgumentException.class, () -> service.initialize(show.getId(), request));
        }
        verify(inventories, never()).saveAndFlush(any());
        verifyNoInteractions(snapshots);
    }

    @Test
    void availabilityExpiresHoldsBeforeReadingAndHidesHeldAndBookedSeats() {
        when(shows.existsById(show.getId())).thenReturn(true);
        ShowInventory inventory = new ShowInventory();
        inventory.setCurrency("INR");
        when(inventories.findById(show.getId())).thenReturn(Optional.of(inventory));
        ShowSeat available = snapshot("1", ShowSeatStatus.AVAILABLE);
        ShowSeat held = snapshot("2", ShowSeatStatus.HELD);
        ShowSeat booked = snapshot("10", ShowSeatStatus.BOOKED);
        when(snapshots.findAllByShowId(show.getId())).thenReturn(List.of(booked, available, held));
        var result = service.getAvailability(show.getId());
        assertEquals(List.of("1", "2", "10"), result.stream().map(ShowSeatResponse::number).toList());
        assertEquals(List.of("AVAILABLE", "UNAVAILABLE", "UNAVAILABLE"),
                result.stream().map(ShowSeatResponse::availability).toList());
        var order = inOrder(expiration, snapshots);
        order.verify(expiration).expireForShow(show.getId(), NOW);
        order.verify(snapshots).findAllByShowId(show.getId());
    }

    private ShowSeat snapshot(String number, ShowSeatStatus status) {
        ShowSeat seat = new ShowSeat();
        seat.setSourceSeat(source);
        seat.setRow("A");
        seat.setNumber(number);
        seat.setStatus(status);
        return seat;
    }

    private void prepareShow() {
        when(shows.findByIdWithVenue(show.getId())).thenReturn(Optional.of(show));
    }

    private void prepareSeats() {
        prepareShow();
        when(seats.findByVenueId(show.getVenue().getId())).thenReturn(List.of(source));
    }

    private ShowInventoryRequest request(String currency, TierPriceRequest... prices) {
        return new ShowInventoryRequest(currency, List.of(prices));
    }

    private TierPriceRequest price(String tier, String amount) {
        return new TierPriceRequest(tier, new BigDecimal(amount));
    }
}
