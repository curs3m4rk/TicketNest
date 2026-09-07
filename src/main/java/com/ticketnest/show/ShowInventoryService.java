package com.ticketnest.show;

import com.ticketnest.common.ConflictException;
import com.ticketnest.booking.BookingExpirationService;
import com.ticketnest.entity.Seat;
import com.ticketnest.entity.Show;
import com.ticketnest.entity.ShowInventory;
import com.ticketnest.entity.ShowSeat;
import com.ticketnest.entity.ShowSeatStatus;
import com.ticketnest.repository.SeatRepository;
import com.ticketnest.repository.ShowInventoryRepository;
import com.ticketnest.repository.ShowRepository;
import com.ticketnest.repository.ShowSeatRepository;
import com.ticketnest.show.dto.ShowInventoryRequest;
import com.ticketnest.show.dto.ShowInventoryResponse;
import com.ticketnest.show.dto.ShowSeatResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ShowInventoryService {
    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;
    private final ShowInventoryRepository inventoryRepository;
    private final ShowSeatRepository showSeatRepository;
    private final BookingExpirationService expirationService;
    private final Clock clock;

    public ShowInventoryService(ShowRepository showRepository, SeatRepository seatRepository,
                                ShowInventoryRepository inventoryRepository, ShowSeatRepository showSeatRepository,
                                BookingExpirationService expirationService, Clock clock) {
        this.showRepository = showRepository;
        this.seatRepository = seatRepository;
        this.inventoryRepository = inventoryRepository;
        this.showSeatRepository = showSeatRepository;
        this.expirationService = expirationService;
        this.clock = clock;
    }

    @Transactional
    public ShowInventoryResponse initialize(UUID showId, ShowInventoryRequest request) {
        Show show = showRepository.findByIdWithVenue(showId)
                .orElseThrow(() -> new EntityNotFoundException("Show with id " + showId + " not found"));
        Instant now = clock.instant();
        if (!show.getStartTime().isAfter(now)) {
            throw new ConflictException("Inventory cannot be initialized for a show that has started");
        }
        if (inventoryRepository.existsById(showId)) {
            throw new ConflictException("Inventory is already initialized for show " + showId);
        }
        String currency = request.currency().trim().toUpperCase(Locale.ROOT);
        try {
            java.util.Currency.getInstance(currency);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Currency must be a recognized ISO 4217 code");
        }

        List<Seat> venueSeats = seatRepository.findByVenueId(show.getVenue().getId());
        if (venueSeats.isEmpty()) {
            throw new ConflictException("The show's venue has no seats");
        }

        Map<String, BigDecimal> prices = normalizePrices(request);
        Set<String> venueTiers = venueSeats.stream()
                .map(seat -> seat.getTier().trim().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        if (!prices.keySet().equals(venueTiers)) {
            Set<String> missing = new HashSet<>(venueTiers);
            missing.removeAll(prices.keySet());
            Set<String> unknown = new HashSet<>(prices.keySet());
            unknown.removeAll(venueTiers);
            throw new IllegalArgumentException("Tier prices must exactly match venue tiers; missing=" + missing + ", unknown=" + unknown);
        }

        ShowInventory inventory = new ShowInventory();
        inventory.setShow(show);
        inventory.setCurrency(currency);
        inventory.setCreatedAt(now);
        inventoryRepository.saveAndFlush(inventory);

        List<ShowSeat> snapshots = venueSeats.stream().map(seat -> {
            ShowSeat snapshot = new ShowSeat();
            snapshot.setInventory(inventory);
            snapshot.setSourceSeat(seat);
            snapshot.setRow(seat.getRow());
            snapshot.setNumber(seat.getNumber());
            snapshot.setTier(seat.getTier().trim().toUpperCase(Locale.ROOT));
            snapshot.setPrice(prices.get(snapshot.getTier()));
            snapshot.setStatus(ShowSeatStatus.AVAILABLE);
            snapshot.setCreatedAt(now);
            return snapshot;
        }).toList();
        showSeatRepository.saveAllAndFlush(snapshots);
        return new ShowInventoryResponse(showId, inventory.getCurrency(), snapshots.size());
    }

    @Transactional
    public List<ShowSeatResponse> getAvailability(UUID showId) {
        if (!showRepository.existsById(showId)) {
            throw new EntityNotFoundException("Show with id " + showId + " not found");
        }
        ShowInventory inventory = inventoryRepository.findById(showId)
                .orElseThrow(() -> new EntityNotFoundException("Inventory for show " + showId + " not found"));
        expirationService.expireForShow(showId, clock.instant());
        return showSeatRepository.findAllByShowId(showId).stream()
                .sorted(Comparator.comparing(ShowSeat::getRow, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ShowSeat::getRow)
                        .thenComparing(ShowSeat::getNumber, ShowInventoryService::compareSeatNumbers))
                .map(seat -> new ShowSeatResponse(
                        seat.getId(), seat.getSourceSeat().getId(), seat.getRow(), seat.getNumber(), seat.getTier(),
                        seat.getPrice(), inventory.getCurrency(),
                        seat.getStatus() == ShowSeatStatus.AVAILABLE ? "AVAILABLE" : "UNAVAILABLE"))
                .toList();
    }

    private Map<String, BigDecimal> normalizePrices(ShowInventoryRequest request) {
        Map<String, BigDecimal> prices = new HashMap<>();
        request.tierPrices().forEach(tierPrice -> {
            String tier = tierPrice.tier().trim().toUpperCase(Locale.ROOT);
            BigDecimal price;
            try {
                price = tierPrice.price().setScale(2, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Price for tier " + tier + " must have at most two decimal places");
            }
            if (prices.putIfAbsent(tier, price) != null) {
                throw new IllegalArgumentException("Tier " + tier + " is priced more than once");
            }
        });
        return prices;
    }

    private static int compareSeatNumbers(String left, String right) {
        boolean leftNumeric = left.matches("\\d+");
        boolean rightNumeric = right.matches("\\d+");
        if (leftNumeric && rightNumeric) {
            int comparison = new BigInteger(left).compareTo(new BigInteger(right));
            return comparison != 0 ? comparison : left.compareTo(right);
        }
        if (leftNumeric != rightNumeric) {
            return leftNumeric ? -1 : 1;
        }
        int comparison = left.compareToIgnoreCase(right);
        return comparison != 0 ? comparison : left.compareTo(right);
    }
}
