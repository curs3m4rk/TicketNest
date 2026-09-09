package com.ticketnest.config;

import com.jayway.jsonpath.JsonPath;
import com.ticketnest.auth.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ApiVersioningIntegrationTest extends BaseIntegrationTest {
    private static final AtomicLong PHONE_COUNTER = new AtomicLong(30_000_000L);

    @Autowired
    MockMvc mockMvc;

    @Test
    void v1AuthenticationIsPublicAndOldAndUnsupportedVersionsDoNotResolve() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String email = "versioning-" + suffix + "@example.com";
        String password = "Password123";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"%s",
                                  "password":"%s",
                                  "firstName":"Version",
                                  "lastName":"Tester",
                                  "phoneNumber":"+1668%d"
                                }
                                """.formatted(email, password, PHONE_COUNTER.getAndIncrement())))
                .andExpect(status().isCreated());

        String loginBody = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(loginBody, "$.token");

        mockMvc.perform(get("/api/v1/shows").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/shows").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v2/shows").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/auth/login").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void generatedOpenApiContainsOnlyV1ApplicationPathsAndMarksPublicAuth() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.version").value("v1"))
                .andExpect(jsonPath("$['paths']['/api/v1/auth/register']").exists())
                .andExpect(jsonPath("$['paths']['/api/v1/shows']").exists())
                .andExpect(jsonPath("$['paths']['/api/v1/venues']").exists())
                .andExpect(jsonPath("$['paths']['/api/v1/bookings']").exists())
                .andExpect(jsonPath("$['paths']['/api/v1/admin/roles']").exists())
                .andExpect(jsonPath("$['paths']['/api/v1/auth/register'].post.security.length()").value(0))
                .andExpect(jsonPath("$['paths']['/api/v1/auth/login'].post.responses['429'].headers['Retry-After']").exists())
                .andExpect(jsonPath("$['paths']['/api/v1/bookings'].post.responses['429'].headers['Retry-After']").exists())
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$['paths']['/auth/register']").doesNotExist())
                .andExpect(jsonPath("$['paths']['/api/shows']").doesNotExist());
    }

    @Test
    void localFrontendOriginCanPreflightAuthenticatedApiRequests() throws Exception {
        mockMvc.perform(options("/api/v1/bookings")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers", org.hamcrest.Matchers.containsStringIgnoringCase("idempotency-key")));
    }
}
