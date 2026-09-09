package com.ticketnest.common.ratelimit;

import com.ticketnest.auth.BaseIntegrationTest;
import com.ticketnest.auth.JwtUtil;
import com.ticketnest.entity.User;
import com.ticketnest.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.rate-limit.login.capacity=2",
        "app.rate-limit.booking-create.capacity=2"
})
class RateLimitIntegrationTest extends BaseIntegrationTest {
    private static final AtomicLong PHONE_COUNTER = new AtomicLong(40_000_000L);

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired JwtUtil jwtUtil;

    @Test
    void loginIsLimitedPerClientIpAndOtherAuthEndpointsRemainUnlimited() throws Exception {
        MockHttpServletRequestBuilder login = fromIp(
                post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"),
                "203.0.113.10");

        mockMvc.perform(login).andExpect(status().isBadRequest());
        mockMvc.perform(fromIp(
                        post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"),
                        "203.0.113.10"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(fromIp(
                        post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"),
                        "203.0.113.10"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value("Rate limit exceeded; retry later"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"));

        mockMvc.perform(fromIp(
                        post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"),
                        "203.0.113.11"))
                .andExpect(status().isBadRequest());

        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(fromIp(
                            post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"),
                            "203.0.113.10"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void bookingCreationIsLimitedPerUserAndCancellationRemainsUnlimited() throws Exception {
        String firstToken = tokenForNewUser();
        String secondToken = tokenForNewUser();

        performInvalidBooking(firstToken, status().isBadRequest());
        performInvalidBooking(firstToken, status().isBadRequest());
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + firstToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.path").value("/api/v1/bookings"));

        performInvalidBooking(secondToken, status().isBadRequest());

        UUID missingBooking = UUID.randomUUID();
        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(post("/api/v1/bookings/{id}/cancel", missingBooking)
                            .header("Authorization", "Bearer " + firstToken))
                    .andExpect(status().isNotFound());
        }
    }

    private void performInvalidBooking(String token,
                                       org.springframework.test.web.servlet.ResultMatcher expectedStatus) throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(expectedStatus);
    }

    private MockHttpServletRequestBuilder fromIp(MockHttpServletRequestBuilder request, String address) {
        return request.with(servletRequest -> {
            servletRequest.setRemoteAddr(address);
            return servletRequest;
        });
    }

    private String tokenForNewUser() {
        User user = new User();
        user.setEmail("rate-limit-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("not-used");
        user.setFirstName("Rate");
        user.setLastName("Limited");
        user.setPhoneNumber("+1669" + PHONE_COUNTER.getAndIncrement());
        user.setCreatedAt(Instant.now());
        userRepository.save(user);
        return jwtUtil.generateToken(user.getEmail());
    }
}
