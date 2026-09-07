package com.ticketnest.booking;

import com.ticketnest.entity.Booking;
import com.ticketnest.entity.BookingStatus;
import com.ticketnest.entity.ShowSeat;
import com.ticketnest.entity.ShowSeatStatus;
import com.ticketnest.repository.BookingRepository;
import com.ticketnest.repository.ShowSeatRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class BookingExpirationService {
    private final BookingRepository bookingRepository;
    private final ShowSeatRepository showSeatRepository;
    private final Clock clock;
    private final int cleanupBatchSize;

    public BookingExpirationService(
            BookingRepository bookingRepository,
            ShowSeatRepository showSeatRepository,
            Clock clock,
            @Value("${app.booking.cleanup-batch-size:100}") int cleanupBatchSize
    ) {
        this.bookingRepository = bookingRepository;
        this.showSeatRepository = showSeatRepository;
        this.clock = clock;
        this.cleanupBatchSize = cleanupBatchSize;
    }

    @Transactional
    public void expireForShow(UUID showId, Instant now) {
        expireByIds(showSeatRepository.findExpiredBookingIdsForShow(showId, now), now);
    }

    @Transactional
    public void expireForSeats(Collection<UUID> showSeatIds, Instant now) {
        if (!showSeatIds.isEmpty()) {
            expireByIds(showSeatRepository.findExpiredBookingIdsForSeats(showSeatIds, now), now);
        }
    }

    @Transactional
    public void expireForUser(UUID userId, Instant now) {
        expireByIds(bookingRepository.findExpiredIdsForUser(userId, now), now);
    }

    @Transactional
    public boolean expireIfDue(UUID bookingId, Instant now) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId).orElse(null);
        if (booking == null || !isDue(booking, now)) {
            return false;
        }
        expire(booking, now);
        return true;
    }

    @Scheduled(fixedDelayString = "${app.booking.cleanup-interval:PT1M}")
    @Transactional
    public void cleanupExpiredHolds() {
        Instant now = clock.instant();
        List<UUID> ids = bookingRepository.findExpiredIdsForUpdate(now, cleanupBatchSize);
        List<Booking> bookings = ids.isEmpty() ? List.of() : bookingRepository.findAllByIdForUpdate(ids);
        bookings.stream().filter(booking -> isDue(booking, now)).forEach(booking -> expire(booking, now));
    }

    private void expireByIds(Collection<UUID> ids, Instant now) {
        List<UUID> sortedIds = new ArrayList<>(ids);
        sortedIds.sort(Comparator.naturalOrder());
        if (sortedIds.isEmpty()) {
            return;
        }
        bookingRepository.findAllByIdForUpdate(sortedIds).stream()
                .filter(booking -> isDue(booking, now))
                .forEach(booking -> expire(booking, now));
    }

    private boolean isDue(Booking booking, Instant now) {
        return booking.getStatus() == BookingStatus.HELD && !booking.getExpiresAt().isAfter(now);
    }

    private void expire(Booking booking, Instant now) {
        releaseSeats(booking, now);
        booking.setStatus(BookingStatus.EXPIRED);
        booking.setUpdatedAt(now);
    }

    public void releaseSeats(Booking booking, Instant now) {
        for (ShowSeat showSeat : showSeatRepository.findAllocatedForUpdate(booking.getId())) {
            if (showSeat.getStatus() == ShowSeatStatus.HELD) {
                showSeat.setStatus(ShowSeatStatus.AVAILABLE);
                showSeat.setAllocatedBooking(null);
                showSeat.setHoldExpiresAt(null);
                showSeat.setUpdatedAt(now);
            }
        }
    }
}
