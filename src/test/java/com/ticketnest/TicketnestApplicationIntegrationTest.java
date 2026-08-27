package com.ticketnest;

import com.ticketnest.auth.BaseIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class TicketnestApplicationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesAndValidatesTheInitialSchema() {
        var applied = flyway.info().applied();

        assertThat(applied).hasSize(1);
        assertThat(applied[0].getVersion().getVersion()).isEqualTo("1");
        assertThat(applied[0].getDescription()).isEqualTo("initial schema");

        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN (
                    'users', 'refresh_tokens', 'venues', 'seats', 'shows',
                    'bookings', 'booking_seats', 'payments', 'notifications'
                  )
                """, Integer.class);

        assertThat(tableCount).isEqualTo(9);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }
}
