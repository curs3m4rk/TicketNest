package com.ticketnest.show;

import com.ticketnest.common.ConflictException;
import com.ticketnest.entity.Show;
import com.ticketnest.entity.Venue;
import com.ticketnest.repository.*;
import com.ticketnest.show.dto.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShowServiceTest {
    @Mock ShowRepository shows;
    @Mock VenueRepository venues;
    @Mock ShowInventoryRepository inventories;
    @InjectMocks ShowService service;

    @Test
    void createRequiresAnExistingVenue() {
        assertThrows(EntityNotFoundException.class, () -> service.createShow(request(UUID.randomUUID())));
        verifyNoInteractions(shows, inventories);
    }

    @Test
    void createEnrichesShowWithVenueAndSeatTiers() {
        Venue venue = venue();
        when(venues.findById(venue.getId())).thenReturn(Optional.of(venue));
        when(shows.save(any(Show.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shows.findSeatTiersByVenueId(venue.getId())).thenReturn(List.of("VIP", "STANDARD"));
        var result = service.createShow(request(venue.getId()));
        assertEquals("Concert", result.title());
        assertEquals(venue.getId(), result.venue().id());
        assertEquals(List.of("VIP", "STANDARD"), result.venue().seatTiers());
    }

    @Test
    void rejectsEqualAndReversedDateBoundsBeforeQuerying() {
        Instant from = Instant.parse("2027-01-01T00:00:00Z");
        for (Instant to : List.of(from, from.minusSeconds(1))) {
            assertThrows(IllegalArgumentException.class, () -> service.getAllShows(
                    new ShowFilter(null, null, from, to), PageRequest.of(0, 10)));
        }
        verifyNoInteractions(shows, venues, inventories);
    }

    @Test
    void initializedInventoryPreventsVenueReassignmentWithoutMutatingShow() {
        Venue original = venue();
        Venue replacement = venue();
        Show show = show(original);
        when(shows.findByIdWithVenue(show.getId())).thenReturn(Optional.of(show));
        when(venues.findById(replacement.getId())).thenReturn(Optional.of(replacement));
        when(inventories.existsById(show.getId())).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.updateShow(show.getId(), request(replacement.getId())));
        assertSame(original, show.getVenue());
        assertEquals("Original", show.getTitle());
        verify(shows, never()).save(any());
    }

    @Test
    void allowsVenueReassignmentBeforeInventoryInitialization() {
        Venue replacement = venue();
        Show show = show(venue());
        when(shows.findByIdWithVenue(show.getId())).thenReturn(Optional.of(show));
        when(venues.findById(replacement.getId())).thenReturn(Optional.of(replacement));
        var result = service.updateShow(show.getId(), request(replacement.getId()));
        assertEquals(replacement.getId(), result.venue().id());
        assertEquals("Concert", result.title());
        verify(shows).save(show);
    }

    @Test
    void allowsMetadataUpdatesWithoutChangingVenue() {
        Venue venue = venue();
        Show show = show(venue);
        when(shows.findByIdWithVenue(show.getId())).thenReturn(Optional.of(show));
        when(venues.findById(venue.getId())).thenReturn(Optional.of(venue));
        assertEquals("Concert", service.updateShow(show.getId(), request(venue.getId())).title());
        verify(shows).save(show);
    }

    @Test
    void initializedInventoryPreventsDeletion() {
        UUID id = UUID.randomUUID();
        when(shows.existsById(id)).thenReturn(true);
        when(inventories.existsById(id)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.deleteShow(id));
        verify(shows, never()).deleteById(any());
    }

    @Test
    void deletesShowWithoutInventory() {
        UUID id = UUID.randomUUID();
        when(shows.existsById(id)).thenReturn(true);
        service.deleteShow(id);
        verify(shows).deleteById(id);
    }

    @Test
    void missingShowCannotBeReadOrDeleted() {
        UUID id = UUID.randomUUID();
        assertThrows(EntityNotFoundException.class, () -> service.getShow(id));
        assertThrows(EntityNotFoundException.class, () -> service.deleteShow(id));
        verify(shows, never()).deleteById(any());
    }

    private Venue venue() {
        Venue venue = new Venue();
        venue.setId(UUID.randomUUID());
        return venue;
    }

    private Show show(Venue venue) {
        Show show = new Show();
        show.setId(UUID.randomUUID());
        show.setVenue(venue);
        show.setTitle("Original");
        return show;
    }

    private ShowRequest request(UUID venueId) {
        return new ShowRequest(venueId, "Concert", "Music", Instant.parse("2027-01-01T00:00:00Z"), "ACTIVE");
    }
}
