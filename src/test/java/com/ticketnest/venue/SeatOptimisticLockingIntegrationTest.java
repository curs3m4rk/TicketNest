package com.ticketnest.venue;

import com.ticketnest.auth.BaseIntegrationTest;
import com.ticketnest.entity.Seat;
import com.ticketnest.entity.Venue;
import com.ticketnest.repository.SeatRepository;
import com.ticketnest.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SeatOptimisticLockingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setup() {
        seatRepository.deleteAll();
        venueRepository.deleteAll();
    }

    @Test
    void updatingStaleSeat_shouldThrowOptimisticLockingFailure() {
        UUID seatId = createSeat().getId();

        Seat firstCopy = inNewTransaction(() -> seatRepository.findById(seatId).orElseThrow());
        Seat staleCopy = inNewTransaction(() -> seatRepository.findById(seatId).orElseThrow());

        firstCopy.setTier("VIP");
        inNewTransaction(() -> seatRepository.saveAndFlush(firstCopy));

        staleCopy.setTier("PREMIUM");
        assertThrows(
                OptimisticLockingFailureException.class,
                () -> inNewTransaction(() -> seatRepository.saveAndFlush(staleCopy))
        );

        Seat persisted = seatRepository.findById(seatId).orElseThrow();
        assertEquals("VIP", persisted.getTier());
        assertEquals(1L, persisted.getVersion());
    }

    private Seat createSeat() {
        Venue venue = new Venue();
        venue.setName("Optimistic Lock Arena");
        venue.setCity("Pune");
        venue.setAddress("1 Locking Road");
        venue.setCreatedAt(Instant.now());
        venue = venueRepository.save(venue);

        Seat seat = new Seat();
        seat.setVenue(venue);
        seat.setRow("A");
        seat.setNumber("1");
        seat.setTier("STANDARD");
        seat.setCreatedAt(Instant.now());
        return seatRepository.saveAndFlush(seat);
    }

    private <T> T inNewTransaction(Supplier<T> action) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> action.get());
    }
}
