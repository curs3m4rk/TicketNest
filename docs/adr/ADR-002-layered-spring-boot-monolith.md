# ADR-002: Layered Spring Boot Monolith

Date: 2026-08-16  
Status: Accepted

## Context

Commits `2ad1c1f` and `4c88cca` introduced the API around show and venue features. The resulting code uses controllers, services, repositories, entities, and request/response DTOs in one deployable Spring Boot application.

## Options Considered

No alternative architecture is recorded in repository history.

## Decision

Build TicketNest as one Spring Boot process. Organize callable features by package, route HTTP behavior through controller -> service -> repository layers, and keep JPA entities out of public API contracts by mapping to DTOs.

## Reasoning

Reason not recoverable from repository history.

## Consequences

### Positive

- One build and deployment unit keeps runtime topology small.
- Controllers, business behavior, persistence, and API contracts have identifiable responsibilities.
- DTOs avoid directly serializing lazy entity graphs.

### Negative

- Package conventions do not enforce module boundaries.
- Shared entity/repository packages can encourage cross-feature coupling as the codebase grows.

### Risks / Trade-offs

- Entity-only domains can look implemented when they have no callable workflow; project status must distinguish scaffolding from features.
- A future split into modules or services would require an explicit replacement ADR, not an undocumented gradual drift.

## Related

- Commits `2ad1c1f`, `4c88cca`, `090e6cb`
- `src/main/java/com/ticketnest`
- `docs/diagrams/architecture.mmd`
