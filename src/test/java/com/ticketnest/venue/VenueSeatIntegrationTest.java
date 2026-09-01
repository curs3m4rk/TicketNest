package com.ticketnest.venue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketnest.auth.BaseIntegrationTest;
import com.ticketnest.entity.Seat;
import com.ticketnest.entity.Venue;
import com.ticketnest.repository.SeatRepository;
import com.ticketnest.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class VenueSeatIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private SeatRepository seatRepository;

    private static final AtomicLong PHONE_COUNTER = new AtomicLong(55_510_000_000L);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String jwtToken;
    private Venue venue;

    @BeforeEach
    void setup() throws Exception {
        seatRepository.deleteAll();
        venueRepository.deleteAll();

        venue = new Venue();
        venue.setName("Test Arena");
        venue.setCity("Pune");
        venue.setAddress("1 Test Road");
        venue.setCreatedAt(Instant.now());
        venue = venueRepository.save(venue);

        String email = "seats-" + UUID.randomUUID() + "@example.com";
        String phoneNumber = "+1" + PHONE_COUNTER.getAndIncrement();
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password123","firstName":"Seat","lastName":"Tester","phoneNumber":"%s"}
                                """.formatted(email, phoneNumber)))
                .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        jwtToken = objectMapper.readTree(login.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void getSeats_shouldReturnOnlyVenueSeatsInRowAndNumberOrder() throws Exception {
        createSeat(venue, "B", "2", "STANDARD");
        createSeat(venue, "A", "2", "VIP");
        createSeat(venue, "A", "1", "VIP");

        Venue otherVenue = new Venue();
        otherVenue.setName("Other Arena");
        otherVenue.setCity("Mumbai");
        otherVenue.setAddress("2 Test Road");
        otherVenue.setCreatedAt(Instant.now());
        createSeat(venueRepository.save(otherVenue), "A", "1", "STANDARD");

        mockMvc.perform(get("/api/venues/{id}/seats", venue.getId())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].row").value("A"))
                .andExpect(jsonPath("$[0].number").value("1"))
                .andExpect(jsonPath("$[0].tier").value("VIP"))
                .andExpect(jsonPath("$[1].number").value("2"))
                .andExpect(jsonPath("$[2].row").value("B"));
    }

    @Test
    void getSeats_existingVenueWithoutSeats_shouldReturnEmptyList() throws Exception {
        mockMvc.perform(get("/api/venues/{id}/seats", venue.getId())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getSeats_missingVenue_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/venues/{id}/seats", UUID.randomUUID())
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSeats_withoutJwt_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/venues/{id}/seats", venue.getId()))
                .andExpect(status().isUnauthorized());
    }

    private void createSeat(Venue seatVenue, String row, String number, String tier) {
        Seat seat = new Seat();
        seat.setVenue(seatVenue);
        seat.setRow(row);
        seat.setNumber(number);
        seat.setTier(tier);
        seat.setCreatedAt(Instant.now());
        seatRepository.save(seat);
    }
}
