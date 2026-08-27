# ADR-007: Containerized Local Runtime

Date: 2026-08-28  
Status: Accepted

## Context

The application requires Java and PostgreSQL and previously had only a database Compose service. Commit `7a4f85b` introduced a complete local stack and application image.

## Options Considered

No alternative packaging or orchestration approach is recorded.

## Decision

Build the application in a Maven/Temurin multi-stage Dockerfile, run the resulting JAR as a non-root user on a Temurin JRE, and use Docker Compose to coordinate the application with PostgreSQL 17, health checks, environment overrides, and a persistent local database volume.

## Reasoning

Reason not recoverable from repository history.

## Consequences

### Positive

- A clean clone can start the whole local stack with one Compose command.
- Build tools are excluded from the runtime image and the process is non-root.
- Health-based startup ordering avoids racing an unavailable database.

### Negative

- Image builds skip tests; verification remains a separate step.
- Compose configuration is development-oriented and is not production infrastructure.
- The application runs HTTP in Compose while direct local defaults use HTTPS.

### Risks / Trade-offs

- Development default credentials/secrets must be replaced in any non-local environment.
- The mutable database volume is still subject to ADR-001's lack of migrations.

## Related

- Commit `7a4f85b`
- `Dockerfile`, `docker-compose.yml`, `.env.example`, `README.md`
