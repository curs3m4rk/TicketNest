# Architecture Decision Records

ADRs preserve significant TicketNest design decisions without rewriting history. Code, configuration, tests, and Git commits are evidence; absent rationale is explicitly identified rather than inferred.

## Index

| ADR | Status | Decision |
| --- | --- | --- |
| [ADR-001](ADR-001-postgresql-jpa-runtime-schema-management.md) | Accepted | PostgreSQL with JPA and runtime Hibernate schema updates |
| [ADR-002](ADR-002-layered-spring-boot-monolith.md) | Accepted | Layered Spring Boot monolith with feature packages and DTO boundaries |
| [ADR-003](ADR-003-stateless-jwt-and-rotating-refresh-tokens.md) | Accepted | Stateless JWT access tokens and rotating opaque refresh tokens |
| [ADR-004](ADR-004-role-based-admin-mutations.md) | Accepted | Role-based authorization for catalog mutations |
| [ADR-005](ADR-005-seat-optimistic-locking.md) | Accepted | Optimistic locking on seat updates |
| [ADR-006](ADR-006-persistent-passwordless-otp-challenges.md) | Accepted | Persistent, rate-limited passwordless OTP challenges |
| [ADR-007](ADR-007-containerized-local-runtime.md) | Accepted | Multi-stage application image and Compose local runtime |

## New ADRs

Use the next sequence number and the format required by `AGENTS.md`. Dates must come from an issue, commit, or decision made in the current task. If a decision changes, keep the old record, set it to `Superseded`, and link the replacement in both records.
