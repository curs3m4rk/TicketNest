# TicketNest Architecture

Last verified against commit `fd95a72` on 2026-08-28.

## Scope and Current Shape

TicketNest is a single-process, layered Spring Boot REST API backed by one PostgreSQL database. Code is organized primarily by feature (`auth`, `show`, `venue`) with shared entities and repositories. It is a modular monolith by deployment shape, although module boundaries are package conventions rather than independently built modules.

The current runtime view is in `diagrams/architecture.mmd`; the current persistence view is in `diagrams/er-diagram.mmd`.

## Technology Stack

| Area | Current technology |
| --- | --- |
| Language/runtime | Java 21 |
| Application | Spring Boot 4.1.0, Spring Web MVC |
| Persistence | Spring Data JPA, Hibernate, PostgreSQL 17 |
| Security | Spring Security, BCrypt, JJWT 0.12.6 (HS256) |
| Validation | Jakarta Bean Validation |
| API documentation | springdoc OpenAPI / Swagger UI |
| Email | Spring Mail / configured SMTP server |
| SMS | Direct Java HTTP client integration with Twilio REST API |
| Observability | Spring Actuator health, SLF4J, structured Logstash-format console logs, MDC correlation ID |
| Build | Maven Wrapper |
| Test | JUnit 5, Spring Boot test/MockMvc, Mockito, PostgreSQL Testcontainers |
| Local deployment | Multi-stage Dockerfile and Docker Compose |

## Package and Module Structure

- `com.ticketnest.auth`: registration, password login, JWT/refresh-token lifecycle, user details, and OTP login.
- `com.ticketnest.show`: show REST controller, service, JPA specifications, and show DTOs.
- `com.ticketnest.venue`: venue/seat REST controller, service, and DTOs.
- `com.ticketnest.entity`: all JPA domain entities, including currently unexposed booking/payment/notification scaffolding.
- `com.ticketnest.repository`: Spring Data repositories. Repositories exist only for users, refresh tokens, OTP challenges, shows, venues, and seats.
- `com.ticketnest.config`: security and OpenAPI configuration.
- `com.ticketnest.common`: shared error/page DTOs, exception mapping, and request correlation logging.

The application is not split into Maven modules. Booking, payment, and notification are not application modules yet: only their entities and enums exist.

## Request Lifecycle

1. `CorrelationIdFilter` accepts a safe `X-Request-ID` or generates a UUID, adds it to the response and MDC, and logs completion data.
2. Spring Security applies route authorization. Public routes are authentication (except logout), health, Swagger, and OpenAPI.
3. `JwtAuthenticationFilter` parses a bearer JWT, verifies its HS256 signature/expiry, and creates a principal whose name is the user UUID and whose authority comes from the token's role claim.
4. Method security applies `ADMIN` checks to show/venue mutations. All other `/api/**` endpoints require any authenticated principal through the filter chain.
5. Spring MVC binds and validates DTOs/model attributes, then invokes a feature controller.
6. Controllers delegate business behavior to services; services use Spring Data repositories and map entities to response DTOs.
7. `GlobalExceptionHandler` maps validation, lookup, OTP, and general failures into `ErrorResponse`, except some authentication controller methods directly return empty 400/401 responses.
8. Hibernate issues SQL against PostgreSQL. Open EntityManager in View is disabled, so required lazy relationships must be fetched or resolved in the service layer.

## API Surface

| Method and path | Access | Behavior |
| --- | --- | --- |
| `POST /auth/register` | Public | Register email/password user |
| `POST /auth/login` | Public | Issue JWT and refresh token |
| `POST /auth/otp/request` | Public | Create and deliver OTP challenge |
| `POST /auth/otp/verify` | Public | Verify OTP and issue login tokens |
| `POST /auth/refresh` | Public | Rotate refresh token and issue new pair |
| `POST /auth/logout` | Authenticated | Revoke supplied token or all user refresh tokens |
| `GET /api/shows` | Authenticated | Filtered, sorted, paginated show catalog |
| `GET /api/shows/{id}` | Authenticated | Show detail |
| `POST/PUT/DELETE /api/shows...` | `ADMIN` | Show mutation |
| `GET /api/venues` | Authenticated | Venue list |
| `GET /api/venues/{id}` | Authenticated | Venue detail |
| `GET /api/venues/{id}/seats` | Authenticated | Ordered seat list |
| `POST/PUT/DELETE /api/venues...` | `ADMIN` | Venue mutation |
| `GET /actuator/health` | Public | Health status |

## Persistence

JPA entities are the only version-controlled schema definition. `spring.jpa.hibernate.ddl-auto` is `update`; no Flyway/Liquibase migrations or SQL DDL exist. Consequently, the ER diagram describes the schema Hibernate is expected to derive for a new/current database, but Git cannot reconstruct the exact sequence of production DDL changes.

Important persistence behavior:

- UUID primary keys are generated for all tables except OTP challenge IDs, which the service generates.
- Relationships are standard foreign keys. Booking-seat is an association entity, not a JPA many-to-many table.
- A seat is unique by venue, row, and number and has an optimistic `@Version` column.
- A booking has a globally unique idempotency key.
- A payment is one-to-one with a booking through a unique foreign key.
- OTP verification uses a pessimistic write lock on the challenge row.
- Timestamps use `Instant`, the JVM default timezone is set to UTC, and Hibernate JDBC timezone is UTC.

## Authentication and Authorization

Password login verifies a BCrypt hash. Successful password or OTP login issues:

- a 15-minute HS256 JWT containing the user UUID as `sub` and role as a custom claim; and
- a random 32-byte opaque refresh token. Only its SHA-256 hash is persisted for 30 days.

Refresh rotates the token. Reuse of a stored revoked token triggers revocation of all active refresh tokens for that user. Logout requires a valid access JWT and revokes either the supplied user-owned refresh token or all of the user's tokens.

OTP challenges store the normalized destination, delivery channel, HMAC-SHA256 code hash, expiry, attempt count, consumption time, and creation time. Verification locks the row and uses constant-time hash comparison. SMTP and Twilio adapters are conditional beans and both are off by default.

Security is stateless. JWT requests do not load the user from the database, so active/role changes take effect for an already issued access token only after it expires.

## Integrations

- PostgreSQL is mandatory for application persistence.
- SMTP is optional and exists only when `app.otp.email.enabled=true`.
- Twilio's Messages REST endpoint is called synchronously when `app.otp.sms.enabled=true`.
- There is no Kafka, Redis, payment gateway, object storage, cache, or notification provider integration in the current code.

## Cross-Cutting Concerns

- DTO validation produces field-grouped error details.
- A controller advice standardizes most failures.
- Every servlet request receives a correlation ID and structured completion log.
- OpenAPI declares global bearer authentication; Swagger/metadata routes are public.
- No application-level retry, circuit breaker, distributed lock, cache, audit log, or general rate limiter exists.

## Testing Strategy

Most HTTP and JPA integration tests inherit a PostgreSQL 17 Testcontainers fixture and use MockMvc. Tests cover password authentication/refresh/logout, show listing/filtering/pagination/sorting, venue-seat lookup, DTO validation, request correlation, and seat optimistic locking. OTP has focused Mockito unit tests for request and verification happy paths.

Known gaps are documented in `PROJECT_STATUS.md`. Notably, booking/payment/notification have no workflow tests and external email/SMS adapters have no contract tests.

## Deployment and Operations

The Dockerfile builds the Maven artifact and runs it as a non-root user on a Java 21 JRE. Compose starts PostgreSQL 17, waits for database health, starts TicketNest over HTTP on port 8080, and probes Actuator health. A named volume persists local data.

Direct local execution defaults to HTTPS port 8443 using the tracked development keystore. No production orchestration, CI/CD configuration, or cloud infrastructure is present.

## Important Constraints and Risks

- Runtime schema updates are not reproducible migrations.
- Booking/payment/notification structures are not callable features.
- Optimistic locking is present on seats but is not yet part of a reservation transaction.
- `booking_seats` uniqueness prevents duplication only within one booking, not cross-booking double sale.
- Catalog enrichment performs one seat-tier query per show/venue response.
- Development secrets and a keystore are tracked; they must not be treated as production credentials.
- The startup runner performs a sample query and writes directly to standard output.

## Planned Architecture

No authoritative roadmap or accepted planned architecture was found in the repository. Booking, payment, and notification entities indicate intended domain direction, but their workflows and integrations must not be described as planned commitments without a new issue/ADR. Any future architecture should be recorded before or with implementation.
