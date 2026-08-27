# ADR-005: Optimistic Locking on Seat Updates

Date: 2026-08-27  
Status: Accepted

## Context

Concurrent writes to the same seat can overwrite each other. Commit `31c8158` added a JPA version field and an integration test that deliberately saves a stale entity copy.

## Options Considered

No alternatives are recorded in repository history.

## Decision

Use JPA `@Version` optimistic locking on `Seat`. A stale update fails with an optimistic-locking exception instead of silently overwriting a newer update.

## Reasoning

The commit message explicitly identifies concurrent update conflict detection as the intent. No broader booking-concurrency rationale or alternatives are recorded.

## Consequences

### Positive

- Lost updates to an individual seat row are detected.
- The behavior is verified against PostgreSQL.

### Negative

- Callers must handle/retry conflicts; no API workflow currently does so.
- The extra version predicate applies to every seat update.

### Risks / Trade-offs

- This does not reserve a seat or prevent the same seat from appearing in multiple bookings.
- A complete booking consistency design may supersede or complement this decision with database constraints or locks.

## Related

- Commit `31c8158`
- `Seat`
- `SeatOptimisticLockingIntegrationTest`
