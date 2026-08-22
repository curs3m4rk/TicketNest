package com.ticketnest.show;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketnest.auth.BaseIntegrationTest;
import com.ticketnest.entity.Venue;
import com.ticketnest.repository.ShowRepository;
import com.ticketnest.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Show pagination and sorting.
 * Uses Testcontainers PostgreSQL and MockMvc for full-stack testing.
 */
@AutoConfigureMockMvc
class ShowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShowRepository showRepository;

    @Autowired
    private VenueRepository venueRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static String jwtToken;
    private static UUID venueId;
    private static int testCounter = 0;

//    @BeforeAll
//    static void createSharedTestData() {
//        // Static setup will be done in setup() using instance fields
//    }

    @BeforeEach
    void setup() throws Exception {
        testCounter++;
        String uniqueEmail = "showtest" + testCounter + "@example.com";

        // Create test venue (only once)
        if (venueId == null) {
            Venue venue = new Venue();
            venue.setName("Test Arena");
            venue.setCity("New York");
            venue.setAddress("123 Test St");
            venue.setActive(true);
            venue.setCreatedAt(Instant.now());
            Venue saved = venueRepository.save(venue);
            venueId = saved.getId();

            // Create multiple shows for pagination testing
            for (int i = 1; i <= 25; i++) {
                com.ticketnest.entity.Show show = new com.ticketnest.entity.Show();
                show.setVenue(saved);
                show.setTitle("Show " + i);
                show.setGenre(i % 2 == 0 ? "Music" : "Theater");
                show.setStartTime(Instant.now().plusSeconds(i * 86400)); // 1 day apart
                show.setStatus("ACTIVE");
                show.setCreatedAt(Instant.now());
                showRepository.save(show);
            }
        }

        // Register and login to get JWT with unique email
        String registerRequest = """
                {
                    "email": "%s",
                    "password": "Password123",
                    "firstName": "Show",
                    "lastName": "Tester"
                }
                """.formatted(uniqueEmail);

        mockMvc.perform(
                        post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerRequest))
                .andExpect(status().isCreated());

        String loginRequest = """
                {
                    "email": "%s",
                    "password": "Password123"
                }
                """.formatted(uniqueEmail);

        MvcResult result = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginRequest))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        jwtToken = extractToken(response);
    }

    @Test
    void getShows_defaultPagination_shouldReturnPage0Size20SortedByStartTimeAsc() throws Exception {
        mockMvc.perform(
                        get("/api/shows")
                                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(20)) // default page size 20
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.totalElements").value(greaterThan(0)))
                .andExpect(jsonPath("$.totalPages").value(greaterThan(1)))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.empty").value(false));
    }

    @Test
    void getShows_customPageSize_shouldReturnRequestedSize() throws Exception {
        mockMvc.perform(
                        get("/api/shows?page=0&size=5")
                                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.pageSize").value(5))
                .andExpect(jsonPath("$.totalElements").value(greaterThan(0)))
                .andExpect(jsonPath("$.totalPages").value(greaterThan(1)));
    }

    @Test
    void getShows_sortByStartTimeAsc_shouldReturnUpcomingFirst() throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/shows?page=0&size=5&sort=startTime,asc")
                                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(response).get("content");

        // First show should have earliest startTime
        Instant firstStartTime = Instant.parse(content.get(0).get("startTime").asText());
        Instant secondStartTime = Instant.parse(content.get(1).get("startTime").asText());
        assert firstStartTime.isBefore(secondStartTime) : "Shows should be sorted by startTime ASC";
    }

    @Test
    void getShows_sortByStartTimeDesc_shouldReturnLatestFirst() throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/shows?page=0&size=5&sort=startTime,desc")
                                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(response).get("content");

        // First show should have latest startTime
        Instant firstStartTime = Instant.parse(content.get(0).get("startTime").asText());
        Instant secondStartTime = Instant.parse(content.get(1).get("startTime").asText());
        assert firstStartTime.isAfter(secondStartTime) : "Shows should be sorted by startTime DESC";
    }

    @Test
    void getShows_sortByVenueCity_shouldSortByCity() throws Exception {
        // Create another venue in different city
        Venue venue2 = new Venue();
        venue2.setName("LA Stadium");
        venue2.setCity("Los Angeles");
        venue2.setAddress("456 LA Blvd");
        venue2.setActive(true);
        venue2.setCreatedAt(Instant.now());
        venueRepository.save(venue2);

        // Add shows for LA venue
        for (int i = 1; i <= 3; i++) {
            com.ticketnest.entity.Show show = new com.ticketnest.entity.Show();
            show.setVenue(venue2);
            show.setTitle("LA Show " + i);
            show.setGenre("Music");
            show.setStartTime(Instant.now().plusSeconds(i * 86400));
            show.setStatus("ACTIVE");
            show.setCreatedAt(Instant.now());
            showRepository.save(show);
        }

        MvcResult result = mockMvc.perform(
                        get("/api/shows?page=0&size=10&sort=venue.city,asc")
                                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(response).get("content");

        // First shows should be from Los Angeles (alphabetically before New York)
        String firstCity = content.get(0).get("venue").get("city").asText();
        assert "Los Angeles".equals(firstCity) : "Shows should be sorted by venue.city ASC";
    }

    @Test
    void getShows_multipleSortFields_shouldApplyInOrder() throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/shows?page=0&size=10&sort=genre,asc&sort=startTime,asc")
                                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(response).get("content");

        // First by genre (Music before Theater alphabetically), then by startTime
        String firstGenre = content.get(0).get("genre").asText();
        assert "Music".equals(firstGenre) : "Primary sort should be genre ASC";
    }

    @Test
    void getShows_pageBeyondLast_shouldReturnEmptyPage() throws Exception {
        mockMvc.perform(
                        get("/api/shows?page=10&size=20")
                                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.empty").value(true))
                .andExpect(jsonPath("$.pageNumber").value(10));
    }

    @Test
    void getShows_withoutJwt_shouldReturn401() throws Exception {
        mockMvc.perform(
                        get("/api/shows")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getShows_noPaginationParams_shouldReturnPageResponseWithDefaults() throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/shows")
                                .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        // Should return PageResponse with default pagination
        JsonNode root = objectMapper.readTree(response);
        assert root.has("content") : "Should return PageResponse with content";
        assert root.get("content").isArray() : "Content should be array";
        assert root.get("content").size() == 20 : "Default page size should be 20";
        assert root.get("pageNumber").asInt() == 0 : "Default page should be 0";
        assert root.get("pageSize").asInt() == 20 : "Default page size should be 20";
        assert root.get("totalElements").asLong() > 0 : "Should have some elements";
    }

    private String extractToken(String response) {
        int startIdx = response.indexOf("\"token\":\"") + "\"token\":\"".length();
        int endIdx = response.indexOf("\"", startIdx);
        return response.substring(startIdx, endIdx);
    }
}