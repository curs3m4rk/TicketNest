# Project Status

Last verified against commit `fd95a72` on 2026-08-28. Status reflects callable behavior in the current `feature/otp-login` working tree, not entity names alone.

## Current Focus

- The latest implementation milestone is passwordless OTP login (`fd95a72`). Email and SMS delivery adapters exist but are disabled by default and require external configuration.
- Establishing and maintaining Git-native engineering memory is the current repository-level task.
- No subsequent feature plan or issue tracker is present in the repository.

## Recently Completed

- [x] Passwordless email/SMS OTP challenge and verification service with hashed codes, expiry, rate limits, attempt limits, one-time consumption, and row locking.
- [x] Docker image and local Compose stack for the application and PostgreSQL.
- [x] Structured JSON request logging with correlation IDs.
- [x] Optimistic versioning on seats and a concurrent-update integration test.
- [x] Show filtering, pagination, sorting, DTO validation, and venue-seat lookup.

## Authentication and Users

### Implemented

- [x] Email/password registration with normalized email, BCrypt hashing, default `USER` role, and active accounts.
- [x] Email/password login.
- [x] HS256 JWT access tokens carrying user ID and role; access-token lifetime is configurable and defaults to 15 minutes.
- [x] Opaque 256-bit refresh tokens stored only as SHA-256 hashes, with a 30-day lifetime.
- [x] Refresh-token rotation; reuse of a revoked token revokes all refresh tokens for that user.
- [x] Logout of one supplied refresh token or all user refresh tokens.
- [x] Passwordless OTP request/verification for email or E.164 phone identifiers.
- [x] OTP codes protected with HMAC-SHA256, expiration, resend cooldown, rolling request cap, attempt cap, one-time consumption, and pessimistic locking during verification.
- [x] OTP verification creates or resolves a user and records email/phone verification time.
- [x] Conditional SMTP email and Twilio SMS delivery adapters.

### Partial / technical debt

- [~] OTP is not usable in the default configuration because both delivery channels are disabled; requesting an OTP returns service unavailable when no sender is enabled.
- [~] OTP tests cover two service happy paths but not controller behavior, rate limiting, expiry, attempt exhaustion, replay, concurrent verification, or real delivery adapters.
- [~] Expired OTP challenges have no cleanup job. `AuthService.cleanupExpired()` exists for refresh tokens but is not scheduled.
- [~] JWT authorization trusts the embedded role until token expiry and does not re-check user activity or role changes per request.
- [~] Password registration supports email only; OTP-created phone-only users can have null names and password.
- [ ] OAuth/SSO is not implemented and no repository-backed plan was found.

## Authorization and API Security

### Implemented

- [x] Stateless Spring Security filter chain; CSRF and HTTP sessions are disabled.
- [x] `/auth/**` is public except authenticated `/auth/logout`.
- [x] health and OpenAPI/Swagger endpoints are public; all other routes require a JWT.
- [x] `ADMIN` role is required for show and venue create/update/delete operations.
- [x] Authenticated `USER` and `ADMIN` principals can read shows, venues, and seats.

### Partial / technical debt

- [~] No API exists for assigning or changing roles; administrators must currently be provisioned directly in persistence/test setup.
- [~] No authorization tests cover every admin-only venue/show mutation.
- [~] The API has no explicit CORS configuration or rate limiting beyond OTP requests.

## Show Catalog

### Implemented

- [x] Authenticated paginated show listing with default `startTime` ascending order.
- [x] Optional case-insensitive exact filters for venue city and genre.
- [x] Inclusive `from` and exclusive `to` UTC instant filters, including invalid-range validation.
- [x] Client-selected page, size, and sort fields via Spring Data `Pageable`.
- [x] Show detail retrieval.
- [x] Admin-only create, update (including venue reassignment), and delete.
- [x] Responses include venue summary and distinct seat tiers.
- [x] PostgreSQL-backed integration coverage for listing, filtering, pagination, sorting, authentication, and invalid filter input.

### Partial / technical debt

- [~] Show `status` and seat `tier` are unconstrained strings rather than enums/reference data.
- [~] Arbitrary Spring Data sort properties are accepted from clients; no explicit allow-list is present.
- [~] Each show response runs a separate seat-tier query, which can produce an N+1 query pattern.
- [~] A startup `CommandLineRunner` executes and prints a sample Bangalore show query on every application start.
- [ ] Public, unauthenticated catalog browsing is not implemented; current security requires a JWT.

## Venues and Seats

### Implemented

- [x] Authenticated venue list and detail.
- [x] Admin-only venue create, update, and delete.
- [x] Venue responses include distinct seat tiers.
- [x] Authenticated venue-seat listing ordered by row and number.
- [x] Database uniqueness for `(venue_id, seat_row, seat_number)`.
- [x] JPA optimistic version field on `Seat`, verified by a concurrent stale-update integration test.

### Partial / technical debt

- [~] There is no seat create/update/delete API or service; seats are managed only through persistence/test setup.
- [~] Optimistic locking protects seat-row updates, but no booking workflow currently uses it to reserve inventory.
- [~] Venue deletion does not explicitly handle dependent shows/seats and may surface a persistence error.

## Booking, Payment, and Notifications

### Persistence scaffolding present

- [~] Entities model bookings, booking-seat associations, one payment per booking, and user notifications.
- [~] Booking and payment lifecycle enums exist with default `HELD` and `PENDING` states.
- [~] Booking idempotency keys are unique, and a booking cannot contain the same seat twice.

### Not implemented as workflows

- [ ] No repositories, services, controllers, DTOs, or tests exist for booking, payment, or notification workflows.
- [ ] No booking hold expiry/release process exists.
- [ ] No database constraint prevents the same seat from appearing in different active bookings for the same show.
- [ ] No show-specific seat inventory or price model exists.
- [ ] No payment provider integration, webhook processing, refund workflow, or idempotent payment orchestration exists.
- [ ] No notification dispatcher or retry mechanism exists.

## Persistence and Schema Management

### Implemented

- [x] PostgreSQL persistence through Spring Data JPA/Hibernate.
- [x] Ten JPA entities/tables: users, refresh tokens, OTP challenges, venues, seats, shows, bookings, booking seats, payments, and notifications.
- [x] Declared foreign keys, selected indexes, and uniqueness constraints are represented in the entities and ER diagram.

### Technical debt

- [~] There are no Flyway/Liquibase migrations or checked-in DDL. Hibernate `ddl-auto: update` mutates schemas at runtime, so historical database evolution and reproducible upgrades cannot be audited from Git.
- [~] Several Java fields rely on Hibernate naming/default column types rather than explicit schema declarations.
- [~] No database migration/rollback tests exist.

## Operations, Observability, and Delivery

### Implemented

- [x] Multi-stage, non-root Java 21 container image.
- [x] Docker Compose application + PostgreSQL 17 stack with health checks and persistent local database volume.
- [x] Actuator health endpoint.
- [x] Structured JSON console logging and validated/generated `X-Request-ID` correlation IDs.
- [x] Swagger UI and OpenAPI bearer authentication scheme.
- [x] HTTPS enabled for direct local runs with a bundled development PKCS12 keystore; Compose disables SSL and publishes HTTP.

### Partial / technical debt

- [~] Development database credentials, JWT/OTP fallback secrets, and keystore password are present in tracked configuration; documentation warns against production use, but startup does not reject insecure defaults.
- [~] There is no production deployment infrastructure, monitoring/metrics export, tracing, alerting, or centralized log configuration.
- [~] No CI/CD workflow exists. A PR checklist now records documentation obligations, but checks are not automated.

## Testing

- [x] PostgreSQL Testcontainers base for integration tests.
- [x] MockMvc coverage for authentication, show catalog, venue-seat lookup, and validation.
- [x] Unit tests for the correlation filter and core OTP happy paths.
- [x] Seat optimistic-locking integration test.
- [x] The full Maven suite currently passes: 44 tests, 0 failures/errors (verified 2026-08-28 with installed Maven and Docker).
- [~] Docker is required for most integration tests; the bare context-load test is not based on the shared Testcontainers fixture and may require a separately running configured PostgreSQL instance.
- [~] The checked-in Windows Maven wrapper failed before Maven startup in the verification environment; installed Maven completed the suite successfully.
- [ ] No booking/payment/notification tests, external-provider contract tests, end-to-end tests, or load tests exist.
