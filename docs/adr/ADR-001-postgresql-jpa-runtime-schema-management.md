# ADR-001: PostgreSQL with JPA and Runtime Schema Management

Date: 2026-08-12  
Status: Accepted

## Context

TicketNest requires relational persistence for users, venues, shows, seats, authentication tokens, and booking-domain data. PostgreSQL configuration was introduced in commit `3011de8`; JPA entities followed in `f7501af`. The current application configures Hibernate with `ddl-auto: update` and contains no versioned migrations.

## Options Considered

No alternatives are recorded in commit messages, documentation, or configuration.

## Decision

Use PostgreSQL as the single application database, access it through Spring Data JPA/Hibernate, and currently allow Hibernate to update the schema at application startup.

## Reasoning

Reason not recoverable from repository history.

## Consequences

### Positive

- The relational entity graph, constraints, and transactions are expressed in Java.
- Spring Data repositories provide a small data-access layer.
- PostgreSQL is used consistently in local Compose and integration tests.

### Negative

- Database evolution is not represented as reviewable, repeatable Git artifacts.
- Runtime updates may differ across databases depending on their prior state.
- Rollback and production upgrade procedures cannot be reconstructed from the repository.

### Risks / Trade-offs

- Entity definitions describe intended current schema, but cannot prove the exact schema of an already-evolved environment.
- Introducing migrations later requires baselining existing databases and superseding the runtime-update part of this ADR.

## Related

- Commits `3011de8`, `f7501af`, `a0ff6db`
- `src/main/resources/application.yml`
- `src/main/java/com/ticketnest/entity`
- `docs/diagrams/er-diagram.mmd`
