package com.ticketnest.booking;

import com.jayway.jsonpath.JsonPath;
import com.ticketnest.auth.BaseIntegrationTest;
import com.ticketnest.auth.JwtUtil;
import com.ticketnest.entity.Role;
import com.ticketnest.entity.BookingStatus;
import com.ticketnest.entity.ShowSeatStatus;
import com.ticketnest.entity.Seat;
import com.ticketnest.entity.Show;
import com.ticketnest.entity.User;
import com.ticketnest.entity.Venue;
import com.ticketnest.repository.BookingRepository;
import com.ticketnest.repository.BookingSeatRepository;
import com.ticketnest.repository.RoleRepository;
import com.ticketnest.repository.SeatRepository;
import com.ticketnest.repository.ShowInventoryRepository;
import com.ticketnest.repository.ShowRepository;
import com.ticketnest.repository.ShowSeatRepository;
import com.ticketnest.repository.UserRepository;
import com.ticketnest.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(BookingIntegrationTest.TestClockConfiguration.class)
class BookingIntegrationTest extends BaseIntegrationTest {
    private static final Instant TEST_NOW = Instant.parse("2027-01-10T10:00:00Z");
    private static final AtomicLong PHONE_COUNTER = new AtomicLong(20_000_000L);

    @Autowired MockMvc mockMvc;
    @Autowired MutableClock clock;
    @Autowired JwtUtil jwtUtil;
    @Autowired RoleRepository roleRepository;
    @Autowired UserRepository userRepository;
    @Autowired VenueRepository venueRepository;
    @Autowired ShowRepository showRepository;
    @Autowired SeatRepository seatRepository;
    @Autowired ShowInventoryRepository inventoryRepository;
    @Autowired ShowSeatRepository showSeatRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired BookingSeatRepository bookingSeatRepository;
    @Autowired BookingExpirationService expirationService;

    private Show show;
    private String adminToken;
    private String firstUserToken;
    private String secondUserToken;

    @BeforeEach
    void setUp() {
        clock.set(TEST_NOW);
        bookingSeatRepository.deleteAll();
        showSeatRepository.deleteAll();
        bookingRepository.deleteAll();
        inventoryRepository.deleteAll();
        seatRepository.deleteAll();
        showRepository.deleteAll();
        venueRepository.deleteAll();

        Venue venue = new Venue();
        venue.setName("Booking Arena");
        venue.setCity("Pune");
        venue.setAddress("10 Hold Street");
        venue.setCreatedAt(TEST_NOW);
        venue = venueRepository.save(venue);

        createSeat(venue, "A", "1", "VIP");
        createSeat(venue, "A", "2", "STANDARD");
        createSeat(venue, "A", "10", "VIP");

        show = new Show();
        show.setVenue(venue);
        show.setTitle("Future Concert");
        show.setGenre("Music");
        show.setStartTime(TEST_NOW.plus(Duration.ofDays(10)));
        show.setStatus("ACTIVE");
        show.setCreatedAt(TEST_NOW);
        show = showRepository.save(show);

        adminToken = tokenFor(true);
        firstUserToken = tokenFor(false);
        secondUserToken = tokenFor(false);
    }

    @Test
    void inventoryInitialization_shouldAuthorizeValidateNormalizeAndSnapshot() throws Exception {
        mockMvc.perform(post("/api/shows/{id}/inventory", show.getId())
                        .header("Authorization", bearer(firstUserToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inventoryPayload()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/shows/{id}/inventory", show.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currency":"INR","tierPrices":[{"tier":"VIP","price":1000}]}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/shows/{id}/inventory", show.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inventoryPayload()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/shows/" + show.getId() + "/seats"))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.seatCount").value(3));

        createSeat(show.getVenue(), "B", "1", "STANDARD");
        mockMvc.perform(get("/api/shows/{id}/seats", show.getId())
                        .header("Authorization", bearer(firstUserToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].number").value("1"))
                .andExpect(jsonPath("$[1].number").value("2"))
                .andExpect(jsonPath("$[2].number").value("10"))
                .andExpect(jsonPath("$[0].price").value(1000.00))
                .andExpect(jsonPath("$[1].price").value(500.00));

        mockMvc.perform(post("/api/shows/{id}/inventory", show.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inventoryPayload()))
                .andExpect(status().isConflict());
    }

    @Test
    void bookingLifecycle_shouldHoldReplayListProtectAndCancel() throws Exception {
        initializeInventory();
        List<UUID> seats = showSeatIds();
        String request = bookingPayload(seats.subList(0, 2));

        String created = mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(firstUserToken))
                        .header("Idempotency-Key", "checkout-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.totalAmount").value(1500.00))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.seats.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        UUID bookingId = UUID.fromString(JsonPath.read(created, "$.id"));

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(firstUserToken))
                        .header("Idempotency-Key", "checkout-1")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId.toString()));

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(firstUserToken))
                        .header("Idempotency-Key", "checkout-1")
                        .contentType(MediaType.APPLICATION_JSON).content(bookingPayload(List.of(seats.get(2)))))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/bookings").header("Authorization", bearer(firstUserToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(bookingId.toString()));

        mockMvc.perform(get("/api/bookings/{id}", bookingId)
                        .header("Authorization", bearer(secondUserToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/bookings/{id}/cancel", bookingId)
                        .header("Authorization", bearer(firstUserToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RELEASED"));
        mockMvc.perform(post("/api/bookings/{id}/cancel", bookingId)
                        .header("Authorization", bearer(firstUserToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RELEASED"));

        mockMvc.perform(get("/api/shows/{id}/seats", show.getId())
                        .header("Authorization", bearer(firstUserToken)))
                .andExpect(jsonPath("$[0].availability").value("AVAILABLE"))
                .andExpect(jsonPath("$[1].availability").value("AVAILABLE"));
    }

    @Test
    void expiredHold_shouldBeReclaimedLazilyAndBookableAgain() throws Exception {
        initializeInventory();
        UUID seatId = showSeatIds().getFirst();
        String created = createBooking(firstUserToken, "expires", List.of(seatId), 201);
        UUID bookingId = UUID.fromString(JsonPath.read(created, "$.id"));

        clock.set(TEST_NOW.plus(Duration.ofMinutes(11)));
        mockMvc.perform(get("/api/shows/{id}/seats", show.getId())
                        .header("Authorization", bearer(secondUserToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].availability").value("AVAILABLE"));
        mockMvc.perform(get("/api/bookings/{id}", bookingId)
                        .header("Authorization", bearer(firstUserToken)))
                .andExpect(jsonPath("$.status").value("EXPIRED"));

        createBooking(secondUserToken, "after-expiry", List.of(seatId), 201);
    }

    @Test
    void scheduledCleanup_shouldExpireBookingAndReleaseAllSeats() throws Exception {
        initializeInventory();
        List<UUID> seatIds = showSeatIds().subList(0, 2);
        String created = createBooking(firstUserToken, "scheduled-expiry", seatIds, 201);
        UUID bookingId = UUID.fromString(JsonPath.read(created, "$.id"));

        clock.set(TEST_NOW.plus(Duration.ofMinutes(11)));
        expirationService.cleanupExpiredHolds();

        assertEquals(BookingStatus.EXPIRED, bookingRepository.findById(bookingId).orElseThrow().getStatus());
        assertTrue(showSeatRepository.findAllByShowId(show.getId()).stream()
                .allMatch(seat -> seat.getStatus() == ShowSeatStatus.AVAILABLE));
    }

    @Test
    void overlappingConcurrentHolds_shouldAllowExactlyOneBooking() throws Exception {
        initializeInventory();
        UUID seatId = showSeatIds().getFirst();
        List<Integer> statuses = concurrentRequests(List.of(
                new Attempt(firstUserToken, "race-a", List.of(seatId)),
                new Attempt(secondUserToken, "race-b", List.of(seatId))));
        assertEquals(List.of(201, 409), statuses);
        assertEquals(1, bookingRepository.count());
    }

    @Test
    void concurrentIdenticalRetries_shouldCreateOnlyOneBooking() throws Exception {
        initializeInventory();
        UUID seatId = showSeatIds().getFirst();
        List<Integer> statuses = concurrentRequests(List.of(
                new Attempt(firstUserToken, "same-key", List.of(seatId)),
                new Attempt(firstUserToken, "same-key", List.of(seatId))));
        assertEquals(List.of(200, 201), statuses);
        assertEquals(1, bookingRepository.count());
    }

    @Test
    void bookingValidation_shouldRejectMissingKeyDuplicatesAndTooManySeats() throws Exception {
        initializeInventory();
        UUID seatId = showSeatIds().getFirst();
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(firstUserToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload(List.of(seatId))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(firstUserToken))
                        .header("Idempotency-Key", "duplicates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingPayload(List.of(seatId, seatId))))
                .andExpect(status().isBadRequest());

        String elevenIds = java.util.stream.IntStream.range(0, 11)
                .mapToObj(ignored -> "\"" + UUID.randomUUID() + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(firstUserToken))
                        .header("Idempotency-Key", "too-many")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showId\":\"" + show.getId() + "\",\"showSeatIds\":[" + elevenIds + "]}"))
                .andExpect(status().isBadRequest());
        assertEquals(0, bookingRepository.count());
    }

    private void initializeInventory() throws Exception {
        mockMvc.perform(post("/api/shows/{id}/inventory", show.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON).content(inventoryPayload()))
                .andExpect(status().isCreated());
    }

    private List<UUID> showSeatIds() throws Exception {
        String json = mockMvc.perform(get("/api/shows/{id}/seats", show.getId())
                        .header("Authorization", bearer(firstUserToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(json, "$[*].id");
        return ids.stream().map(UUID::fromString).toList();
    }

    private String createBooking(String token, String key, List<UUID> seatIds, int expectedStatus) throws Exception {
        return mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearer(token)).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(bookingPayload(seatIds)))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
    }

    private List<Integer> concurrentRequests(List<Attempt> attempts) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(attempts.size());
        CountDownLatch ready = new CountDownLatch(attempts.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Attempt attempt : attempts) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return mockMvc.perform(post("/api/bookings")
                                    .header("Authorization", bearer(attempt.token()))
                                    .header("Idempotency-Key", attempt.key())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(bookingPayload(attempt.seatIds())))
                            .andReturn().getResponse().getStatus();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
            Collections.sort(statuses);
            return statuses;
        } finally {
            executor.shutdownNow();
        }
    }

    private String inventoryPayload() {
        return """
                {"currency":"inr","tierPrices":[
                  {"tier":" vip ","price":1000.00},
                  {"tier":"standard","price":500.00}
                ]}
                """;
    }

    private String bookingPayload(List<UUID> ids) {
        String values = ids.stream().map(id -> "\"" + id + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"showId\":\"" + show.getId() + "\",\"showSeatIds\":[" + values + "]}";
    }

    private void createSeat(Venue venue, String row, String number, String tier) {
        Seat seat = new Seat();
        seat.setVenue(venue);
        seat.setRow(row);
        seat.setNumber(number);
        seat.setTier(tier);
        seat.setCreatedAt(TEST_NOW);
        seatRepository.save(seat);
    }

    private String tokenFor(boolean admin) {
        User user = new User();
        user.setEmail("booking-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("not-used");
        user.setFirstName("Booking");
        user.setLastName("Tester");
        user.setPhoneNumber("+1668" + PHONE_COUNTER.getAndIncrement());
        user.setCreatedAt(TEST_NOW);
        user.getRoles().add(roleRepository.findByName(Role.USER).orElseThrow());
        if (admin) {
            user.getRoles().add(roleRepository.findByName(Role.ADMIN).orElseThrow());
        }
        userRepository.save(user);
        return jwtUtil.generateToken(user.getEmail());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Attempt(String token, String key, List<UUID> seatIds) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(TEST_NOW);
        }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        MutableClock(Instant initial) {
            this.instant = new AtomicReference<>(initial);
        }

        void set(Instant value) {
            instant.set(value);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
