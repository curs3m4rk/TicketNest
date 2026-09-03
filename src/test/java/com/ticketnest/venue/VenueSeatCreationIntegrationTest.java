package com.ticketnest.venue;

import com.ticketnest.auth.BaseIntegrationTest;
import com.ticketnest.auth.JwtUtil;
import com.ticketnest.entity.Role;
import com.ticketnest.entity.Seat;
import com.ticketnest.entity.User;
import com.ticketnest.entity.Venue;
import com.ticketnest.repository.RoleRepository;
import com.ticketnest.repository.SeatRepository;
import com.ticketnest.repository.UserRepository;
import com.ticketnest.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class VenueSeatCreationIntegrationTest extends BaseIntegrationTest {

    private static final AtomicLong PHONE_COUNTER = new AtomicLong(10_000_000L);

    @Autowired private MockMvc mockMvc;
    @Autowired private VenueRepository venueRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private JwtUtil jwtUtil;

    private Venue venue;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        seatRepository.deleteAll();
        venueRepository.deleteAll();

        venue = new Venue();
        venue.setName("Bulk Seat Arena");
        venue.setCity("Pune");
        venue.setAddress("12 Layout Road");
        venue.setCreatedAt(Instant.now());
        venue = venueRepository.save(venue);

        adminToken = createToken(true);
        userToken = createToken(false);
    }

    @Test
    void createSeats_shouldExpandNormalizePersistAndReturnNaturalOrder() throws Exception {
        mockMvc.perform(post("/api/venues/{id}/seats", venue.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ranges": [
                                    {"row":" a ","startNumber":1,"endNumber":10,"tier":" vip "},
                                    {"row":"b","startNumber":1,"endNumber":2,"tier":"standard"}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/venues/" + venue.getId() + "/seats"))
                .andExpect(jsonPath("$.venueId").value(venue.getId().toString()))
                .andExpect(jsonPath("$.createdCount").value(12));

        List<Seat> seats = seatRepository.findByVenueId(venue.getId());
        assertEquals(12, seats.size());
        assertTrue(seats.stream().filter(seat -> seat.getRow().equals("A"))
                .allMatch(seat -> seat.getTier().equals("VIP")));
        assertTrue(seats.stream().filter(seat -> seat.getRow().equals("B"))
                .allMatch(seat -> seat.getTier().equals("STANDARD")));
        assertTrue(seats.stream().allMatch(seat -> seat.getVenue().getId().equals(venue.getId())));
        assertTrue(seats.stream().allMatch(seat -> seat.getCreatedAt() != null));
        assertTrue(seats.stream().allMatch(seat -> seat.getVersion() != null));

        mockMvc.perform(get("/api/venues/{id}/seats", venue.getId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].row").value("A"))
                .andExpect(jsonPath("$[0].number").value("1"))
                .andExpect(jsonPath("$[1].number").value("2"))
                .andExpect(jsonPath("$[9].number").value("10"))
                .andExpect(jsonPath("$[10].row").value("B"))
                .andExpect(jsonPath("$[10].number").value("1"));
    }

    @Test
    void createSeats_duplicateExistingSeat_shouldReturnConflictAndRollbackBatch() throws Exception {
        createSeat("A", "2", "VIP");

        mockMvc.perform(post("/api/venues/{id}/seats", venue.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ranges":[{"row":"A","startNumber":1,"endNumber":3,"tier":"STANDARD"}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));

        List<Seat> seats = seatRepository.findByVenueId(venue.getId());
        assertEquals(1, seats.size());
        assertEquals("2", seats.getFirst().getNumber());
        assertEquals("VIP", seats.getFirst().getTier());
    }

    @Test
    void createSeats_invalidRanges_shouldReturnBadRequestWithoutInserting() throws Exception {
        List<String> invalidRequests = List.of(
                "{\"ranges\":[]}",
                "{\"ranges\":[null]}",
                "{\"ranges\":[{\"row\":\"A\",\"startNumber\":3,\"endNumber\":1,\"tier\":\"VIP\"}]}",
                "{\"ranges\":[{\"row\":\"A\",\"startNumber\":0,\"endNumber\":1,\"tier\":\"VIP\"}]}",
                "{\"ranges\":[{\"row\":\"A\",\"startNumber\":1,\"endNumber\":3,\"tier\":\"VIP\"},"
                        + "{\"row\":\" a \",\"startNumber\":3,\"endNumber\":4,\"tier\":\"STANDARD\"}]}",
                "{\"ranges\":[{\"row\":\"A\",\"startNumber\":1,\"endNumber\":10001,\"tier\":\"VIP\"}]}"
        );

        for (String request : invalidRequests) {
            mockMvc.perform(post("/api/venues/{id}/seats", venue.getId())
                            .header("Authorization", bearer(adminToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isBadRequest());
        }

        assertEquals(0, seatRepository.count());
    }

    @Test
    void createSeats_shouldEnforceAuthenticationAuthorizationAndVenueExistence() throws Exception {
        String payload = """
                {"ranges":[{"row":"A","startNumber":1,"endNumber":1,"tier":"VIP"}]}
                """;

        mockMvc.perform(post("/api/venues/{id}/seats", venue.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/venues/{id}/seats", venue.getId())
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/venues/{id}/seats", UUID.randomUUID())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isNotFound());

        assertEquals(0, seatRepository.count());
    }

    @Test
    void createSeats_concurrentDuplicateBatches_shouldAllowOnlyOneBatch() throws Exception {
        String payload = """
                {"ranges":[{"row":"C","startNumber":1,"endNumber":2,"tier":"STANDARD"}]}
                """;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int requestNumber = 0; requestNumber < 2; requestNumber++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return mockMvc.perform(post("/api/venues/{id}/seats", venue.getId())
                                    .header("Authorization", bearer(adminToken))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(payload))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
            Collections.sort(statuses);

            assertEquals(List.of(201, 409), statuses);
            assertEquals(2, seatRepository.count());
        } finally {
            executor.shutdownNow();
        }
    }

    private Seat createSeat(String row, String number, String tier) {
        Seat seat = new Seat();
        seat.setVenue(venue);
        seat.setRow(row);
        seat.setNumber(number);
        seat.setTier(tier);
        seat.setCreatedAt(Instant.now());
        return seatRepository.saveAndFlush(seat);
    }

    private String createToken(boolean admin) {
        Role userRole = roleRepository.findByName(Role.USER).orElseThrow();
        User user = new User();
        user.setEmail("seat-api-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("not-used");
        user.setFirstName("Seat");
        user.setLastName("API");
        user.setPhoneNumber("+1559" + PHONE_COUNTER.getAndIncrement());
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        user.getRoles().add(userRole);
        if (admin) {
            user.getRoles().add(roleRepository.findByName(Role.ADMIN).orElseThrow());
        }
        user = userRepository.save(user);
        assertNotNull(user.getId());
        return jwtUtil.generateToken(user.getEmail());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
