package com.ticketnest.validation;

import com.ticketnest.auth.BaseIntegrationTest;
import com.ticketnest.auth.JwtUtil;
import com.ticketnest.entity.Role;
import com.ticketnest.entity.User;
import com.ticketnest.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ValidationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private String adminToken;

    @BeforeEach
    void setupAdmin() {
        String email = "validation-admin-" + UUID.randomUUID() + "@example.com";
        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode("Password123"));
        admin.setFirstName("Validation");
        admin.setLastName("Admin");
        admin.setRole(Role.ADMIN);
        admin.setActive(true);
        admin.setCreatedAt(Instant.now());
        userRepository.save(admin);
        adminToken = jwtUtil.generateToken(email, Role.ADMIN.name());
    }

    @Test
    void register_invalidFields_shouldReturnMessagesByField() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"invalid","password":"short","firstName":"","lastName":"X"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.validationErrors.email", hasItem("Email must be valid")))
                .andExpect(jsonPath("$.validationErrors.password", hasItem("Password must be between 8 and 100 characters")))
                .andExpect(jsonPath("$.validationErrors.firstName", hasItem("First name is required")))
                .andExpect(jsonPath("$.validationErrors.lastName", hasItem("Last name must be between 2 and 50 characters")));
    }

    @Test
    void createVenue_invalidFields_shouldReturnMessagesByField() throws Exception {
        mockMvc.perform(post("/api/venues")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","city":"X","address":"abc"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.name", hasItem("Name is required")))
                .andExpect(jsonPath("$.validationErrors.city", hasItem("City must be between 2 and 100 characters")))
                .andExpect(jsonPath("$.validationErrors.address", hasItem("Address must be between 5 and 255 characters")));
    }

    @Test
    void createShow_invalidFields_shouldReturnMessagesByField() throws Exception {
        mockMvc.perform(post("/api/shows")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"X","genre":"","status":"","startTime":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.venueId", hasItem("Venue ID is required")))
                .andExpect(jsonPath("$.validationErrors.title", hasItem("Title must be between 2 and 150 characters")))
                .andExpect(jsonPath("$.validationErrors.genre", hasItem("Genre is required")))
                .andExpect(jsonPath("$.validationErrors.status", hasItem("Status is required")))
                .andExpect(jsonPath("$.validationErrors.startTime", hasItem("Start time is required")));
    }

    @Test
    void refresh_blankToken_shouldReturnFieldMessage() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.refreshToken", hasItem("Refresh token is required")));
    }

    @Test
    void showFilter_tooLong_shouldReturnFieldMessage() throws Exception {
        mockMvc.perform(get("/api/shows")
                        .param("city", "C".repeat(101))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.city", hasItem("City must be at most 100 characters")));
    }
}
