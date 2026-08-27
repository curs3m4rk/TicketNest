package com.ticketnest.config;

import com.ticketnest.TicketnestApplication;
import org.flywaydb.core.Flyway;
import org.hibernate.tool.schema.spi.SchemaManagementException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class SchemaValidationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Test
    void applicationRefusesToStartWhenSchemaDoesNotMatchEntities() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();

        try (var connection = postgres.createConnection("")) {
            connection.createStatement().execute("ALTER TABLE venues DROP COLUMN city");
        } catch (Exception exception) {
            throw new AssertionError("Could not prepare an incompatible schema", exception);
        }

        assertThatThrownBy(() -> new SpringApplicationBuilder(TicketnestApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "DB_URL=" + postgres.getJdbcUrl(),
                        "DB_USERNAME=" + postgres.getUsername(),
                        "DB_PASSWORD=" + postgres.getPassword(),
                        "JWT_SECRET=ticketnest-integration-test-jwt-secret-at-least-32-bytes-long",
                        "spring.flyway.enabled=false",
                        "spring.jpa.hibernate.ddl-auto=validate",
                        "app.jwt.expiration-ms=900000")
                .run())
                .rootCause()
                .isInstanceOf(SchemaManagementException.class)
                .hasMessageContaining("missing column")
                .hasMessageContaining("city");
    }
}
