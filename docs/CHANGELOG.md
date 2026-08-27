# Engineering Changelog

Meaningful changes reconstructed from Git history. Dates are commit dates; entries do not imply a formal release.

## Unreleased

### Documentation

- Added canonical cross-agent engineering rules, current architecture/status documentation, evidence-based ADRs, and synchronized Mermaid diagrams.
- Added a lightweight `CLAUDE.md` pointer to the canonical `AGENTS.md` policy.
- Added a pull-request checklist for engineering-memory maintenance.

## 2026-08-28

### Added

- Added passwordless OTP request and verification for email/SMS, persistent hashed challenges, rate and attempt limits, SMTP and Twilio adapters, and OTP service tests (`fd95a72`).
- Added a multi-stage non-root application image, a complete application/PostgreSQL Compose stack, health checks, environment overrides, and local-run documentation (`7a4f85b`).

### Database

- Added OTP challenge persistence and user email/phone verification fields (`fd95a72`).

## 2026-08-27

### Added

- Added structured JSON request-completion logging and correlation/request IDs (`8511e8d`).
- Added seat optimistic locking and a PostgreSQL concurrency test (`31c8158`).
- Added DTO/model-attribute validation with field-level API errors (`81ab3fc`).
- Added authenticated seat listing by venue (`c00c7ba`).
- Added combined show filtering by city, genre, and time range through JPA Specifications (`16ab713`).

## 2026-08-23

### Added

- Added paginated/sortable show listing, a stable page response contract, and show integration coverage (`6e8625b`).
- Expanded authentication integration tests for JWT protection, refresh rotation/reuse, logout, and validation (`7d0933e`).

## 2026-08-22

### Testing

- Added PostgreSQL Testcontainers integration-test infrastructure and initial registration tests (`45a5da3`).

## 2026-08-19

### Security

- Added `USER`/`ADMIN` roles and admin-only show/venue mutations (`98dece3`).
- Added JWT authentication plus hashed, rotating refresh tokens for login, refresh, and logout (`fb9cde4`).
- Added registration with BCrypt password hashing (`cc6c207`).

### Infrastructure

- Added HTTPS development configuration and Swagger bearer support (`ad36b8f`).

## 2026-08-16 to 2026-08-18

### Added

- Added the Spring Security filter-chain foundation (`30971c8`).
- Added consistent REST error responses (`090e6cb`).
- Added show and venue CRUD APIs with DTO/service/repository separation (`4c88cca`).
- Added catalog show endpoints containing venue details (`2ad1c1f`).
- Added Swagger/OpenAPI (`75fdc33`).

### Database

- Added booking, booking-seat, payment, notification, and lifecycle entity scaffolding (`a0ff6db`).
- Added initial repositories and a city/date show query (`ba61f43`).

## 2026-08-12 to 2026-08-13

### Added

- Initialized the Java/Spring Boot/Maven project and README (`a8c44cf`).
- Added PostgreSQL configuration and local Compose database (`3011de8`).
- Added initial user, venue, show, and seat entities and relationships (`f7501af`).

### Changed

- Replaced the initial properties file with YAML configuration (`c9372ba`).
