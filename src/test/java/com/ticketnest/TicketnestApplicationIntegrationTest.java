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
    void flywayCreatesAndValidatesTheSchema() {
        var applied = flyway.info().applied();

        assertThat(applied).hasSize(3);
        assertThat(applied[0].getVersion().getVersion()).isEqualTo("1");
        assertThat(applied[0].getDescription()).isEqualTo("initial schema");
        assertThat(applied[1].getVersion().getVersion()).isEqualTo("2");
        assertThat(applied[1].getDescription()).isEqualTo("add user phone");
        assertThat(applied[2].getVersion().getVersion()).isEqualTo("3");
        assertThat(applied[2].getDescription()).isEqualTo("database backed roles");

        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN (
                    'users', 'refresh_tokens', 'venues', 'seats', 'shows',
                    'bookings', 'booking_seats', 'payments', 'notifications',
                    'roles', 'role_permissions', 'user_roles'
                  )
                """, Integer.class);

        assertThat(tableCount).isEqualTo(12);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }
}
