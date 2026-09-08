package com.ticketnest.config;

import com.ticketnest.admin.AdminRoleController;
import com.ticketnest.auth.AuthController;
import com.ticketnest.booking.BookingController;
import com.ticketnest.show.ShowController;
import com.ticketnest.venue.VenueController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ApiVersioningTest {

    @Test
    void everyApplicationControllerUsesTheV1Prefix() {
        Map<Class<?>, String> mappings = Map.of(
                AuthController.class, "/api/v1/auth",
                ShowController.class, "/api/v1/shows",
                VenueController.class, "/api/v1/venues",
                BookingController.class, "/api/v1/bookings",
                AdminRoleController.class, "/api/v1/admin"
        );

        mappings.forEach((controller, expected) -> assertArrayEquals(
                new String[]{expected},
                controller.getAnnotation(RequestMapping.class).value(),
                controller.getSimpleName()));
    }
}
