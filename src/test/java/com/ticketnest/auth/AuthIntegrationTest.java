package com.ticketnest.auth;

import com.ticketnest.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.is;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void registerUser_shouldCreateUser() throws Exception {

        String request = """
                {
                    "email": "test@example.com",
                    "password": "Password123",
                    "firstName": "Test",
                    "lastName": "User"
                }
                """;

        mockMvc.perform(
                        post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.firstName").value("Test"))
                .andExpect(jsonPath("$.lastName").value("User"))
                .andExpect(jsonPath("$.role").value("USER"));

        // Verify it actually reached the database
        assert userRepository.existsByEmail("test@example.com");
    }

    @Test
    void loginUser_shouldReturnTokens() throws Exception {

        // Register user first
        String registerRequest = """
                {
                    "email": "login@example.com",
                    "password": "Password123",
                    "firstName": "Login",
                    "lastName": "User"
                }
                """;

        mockMvc.perform(
                        post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerRequest)
                )
                .andExpect(status().isCreated());

        // Login
        String loginRequest = """
                {
                    "email": "login@example.com",
                    "password": "Password123"
                }
                """;

        mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginRequest)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.email").value("login@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void registerUser_duplicateEmail_shouldReject() throws Exception {
        String request = """
                {
                    "email": "duplicate@example.com",
                    "password": "Password123",
                    "firstName": "First",
                    "lastName": "User"
                }
                """;

        // First registration succeeds
        mockMvc.perform(
                        post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isCreated());

        // Second registration with same email should fail
        mockMvc.perform(
                        post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginUser_wrongPassword_shouldReject() throws Exception {
        String registerRequest = """
                {
                    "email": "wrongpass@example.com",
                    "password": "Password123",
                    "firstName": "Wrong",
                    "lastName": "Pass"
                }
                """;

        mockMvc.perform(
                        post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerRequest)
                )
                .andExpect(status().isCreated());

        String loginRequest = """
                {
                    "email": "wrongpass@example.com",
                    "password": "WrongPassword123"
                }
                """;

        mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginRequest)
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getShows_withoutJwt_shouldReturn401() throws Exception {
        mockMvc.perform(
                        get("/api/shows")
                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-ID"));
    }

    @Test
    void getShows_withValidJwt_shouldReturn200() throws Exception {
        // Register and login to get tokens
        String registerRequest = """
                {
                    "email": "jwtuser@example.com",
                    "password": "Password123",
                    "firstName": "JWT",
                    "lastName": "User"
                }
                """;

        mockMvc.perform(
                        post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerRequest)
                )
                .andExpect(status().isCreated());

        String loginRequest = """
                {
                    "email": "jwtuser@example.com",
                    "password": "Password123"
                }
                """;

        String token = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginRequest)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Parse token from response (simple extraction for test)
        String jwtToken = extractToken(token);

        // Access protected endpoint with valid JWT
        mockMvc.perform(
                        get("/api/shows")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Authorization", "Bearer " + jwtToken)
                )
                .andExpect(status().isOk());
    }

    private String extractToken(String response) {
        // Extract access token from login response
        int startIdx = response.indexOf("\"token\":\"") + "\"token\":\"".length();
        int endIdx = response.indexOf("\"", startIdx);
        return response.substring(startIdx, endIdx);
    }

    private String extractRefreshToken(String response) {
        // Extract refresh token from login response
        int startIdx = response.indexOf("\"refreshToken\":\"") + "\"refreshToken\":\"".length();
        int endIdx = response.indexOf("\"", startIdx);
        return response.substring(startIdx, endIdx);
    }

    @Test
    void refreshToken_shouldReturnNewTokenPair() throws Exception {
        // Register and login to get initial tokens
        String registerRequest = """
                {
                    "email": "refreshtoken@example.com",
                    "password": "Password123",
                    "firstName": "Refresh",
                    "lastName": "User"
                }
                """;

        mockMvc.perform(
                        post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerRequest)
                )
                .andExpect(status().isCreated());

        String loginRequest = """
                {
                    "email": "refreshtoken@example.com",
                    "password": "Password123"
                }
                """;

        String loginResponse = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginRequest)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String refreshToken = extractRefreshToken(loginResponse);

        // Refresh token should return new pair
        String refreshRequest = """
                {
                    "refreshToken": "%s"
                }
                """.formatted(refreshToken);

        mockMvc.perform(
                        post("/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(refreshRequest)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.refreshToken").value(is(not(refreshToken))));
    }

    private String loginResponseContainsToken(String response) {
        int startIdx = response.indexOf("\"token\":\"") + "\"token\":\"".length();
        int endIdx = response.indexOf("\"", startIdx);
        return response.substring(startIdx, endIdx);
    }

    @Test
    void refreshToken_oldTokenReuse_shouldReject() throws Exception {
        // Register and login to get initial tokens
        String registerRequest = """
                {
                    "email": "reuse@example.com",
                    "password": "Password123",
                    "firstName": "Reuse",
                    "lastName": "User"
                }
                """;

        mockMvc.perform(
                        post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerRequest)
                )
                .andExpect(status().isCreated());

        String loginRequest = """
                {
                    "email": "reuse@example.com",
                    "password": "Password123"
                }
                """;

        String loginResponse = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginRequest)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String firstRefreshToken = extractRefreshToken(loginResponse);

        // First refresh should succeed
        String refreshRequest1 = """
                {
                    "refreshToken": "%s"
                }
                """.formatted(firstRefreshToken);

        mockMvc.perform(
                        post("/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(refreshRequest1)
                )
                .andExpect(status().isOk());

        // Second refresh with the same (now revoked) token should fail
        String refreshRequest2 = """
                {
                    "refreshToken": "%s"
                }
                """.formatted(firstRefreshToken);

        mockMvc.perform(
                        post("/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(refreshRequest2)
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_withoutRefreshToken_shouldRevokeAll() throws Exception {
        // Register and login to get tokens
        String registerRequest = """
                {
                    "email": "logout@example.com",
                    "password": "Password123",
                    "firstName": "Logout",
                    "lastName": "User"
                }
                """;

        mockMvc.perform(
                        post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(registerRequest)
                )
                .andExpect(status().isCreated());

        String loginRequest = """
                {
                    "email": "logout@example.com",
                    "password": "Password123"
                }
                """;

        String loginResponse = mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginRequest)
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String accessToken = extractToken(loginResponse);
        String refreshToken = extractRefreshToken(loginResponse);

        // Logout without refresh token should revoke all tokens
        String logoutRequest = """
                {
                }
                """;

        mockMvc.perform(
                        post("/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(logoutRequest)
                                .header("Authorization", "Bearer " + accessToken)
                )
                .andExpect(status().isNoContent());

        // The old refresh token should now be revoked, reuse should fail
mockMvc.perform(
                        post("/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                     {
                                         "refreshToken": "%s"
                                     }
                                     """.formatted(refreshToken))
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerValidation_shouldRejectInvalidEmail() throws Exception {
        String request = """
                {
                    "email": "invalid",
                    "password": "Password123",
                    "firstName": "Test",
                    "lastName": "User"
                }
                """;

        mockMvc.perform(
                        post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerValidation_shouldRejectShortPassword() throws Exception {
        String request = """
                {
                    "email": "shortpass@example.com",
                    "password": "short",
                    "firstName": "Test",
                    "lastName": "User"
                }
                """;

        mockMvc.perform(
                        post("/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginValidation_shouldRejectMissingPassword() throws Exception {
        String request = """
                {
                    "email": "test@example.com"
                }
                """;

        mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest());
    }
}
