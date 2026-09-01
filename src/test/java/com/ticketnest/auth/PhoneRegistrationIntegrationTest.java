package com.ticketnest.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PhoneRegistrationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void register_duplicatePhoneNumber_shouldRejectSecondUser() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "phone-one@example.com",
                                    "password": "Password123",
                                    "firstName": "Phone",
                                    "lastName": "One",
                                    "phoneNumber": "+15550000101"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "phone-two@example.com",
                                    "password": "Password123",
                                    "firstName": "Phone",
                                    "lastName": "Two",
                                    "phoneNumber": "+15550000101"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_differentPhoneNumbers_shouldCreateBothUsers() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "different-phone-one@example.com",
                                    "password": "Password123",
                                    "firstName": "Different",
                                    "lastName": "One",
                                    "phoneNumber": "+15550000102"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "different-phone-two@example.com",
                                    "password": "Password123",
                                    "firstName": "Different",
                                    "lastName": "Two",
                                    "phoneNumber": "+15550000103"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void register_missingPhoneNumber_shouldReturnFieldValidationError() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "missing-phone@example.com",
                                    "password": "Password123",
                                    "firstName": "Missing",
                                    "lastName": "Phone"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.phoneNumber", hasItem("Phone number is required")));
    }

    @Test
    void register_blankPhoneNumber_shouldReturnFieldValidationError() throws Exception {
        assertInvalidPhone("blank-phone@example.com", "", "Phone number is required");
    }

    @Test
    void register_localPhoneNumber_shouldReturnFieldValidationError() throws Exception {
        assertInvalidPhone("local-phone@example.com", "9876543210", "Phone number must be a valid E.164 number");
    }

    @Test
    void register_overlongPhoneNumber_shouldReturnFieldValidationError() throws Exception {
        assertInvalidPhone("overlong-phone@example.com", "+1234567890123456", "Phone number must be at most 16 characters");
    }

    @Test
    void register_phoneNumberWithZeroCountryCode_shouldReturnFieldValidationError() throws Exception {
        assertInvalidPhone("zero-country-code@example.com", "+0123456789", "Phone number must be a valid E.164 number");
    }

    private void assertInvalidPhone(String email, String phoneNumber, String expectedMessage) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "%s",
                                    "password": "Password123",
                                    "firstName": "Invalid",
                                    "lastName": "Phone",
                                    "phoneNumber": "%s"
                                }
                                """.formatted(email, phoneNumber)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.phoneNumber", hasItem(expectedMessage)));
    }
}
