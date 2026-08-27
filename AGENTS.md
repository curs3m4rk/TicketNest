# TicketNest Agent Instructions

This file is the canonical, agent-agnostic engineering policy for this repository. The repository is the source of truth; do not rely on earlier AI conversations.

## Before Implementing a Task

1. Read this file.
2. Read `docs/PROJECT_STATUS.md`.
3. Read the relevant sections of `docs/ARCHITECTURE.md`.
4. Read relevant ADRs under `docs/adr/`.
5. Check `docs/diagrams/architecture.mmd` when changing runtime components or interactions.
6. Check `docs/diagrams/er-diagram.mmd` when changing persistence.
7. Inspect the current implementation and Git history before relying on documentation that may have drifted.

Respect documented decisions unless the task intentionally changes them. If a decision changes, create a new ADR, mark the old ADR `Superseded`, and link both records. Never silently rewrite architectural history.

## Repository Facts and Commands

- Runtime: Java 21, Spring Boot, Maven, PostgreSQL.
- Main code: `src/main/java/com/ticketnest`.
- Tests: `src/test/java/com/ticketnest`.
- Configuration: `src/main/resources/application.yml`.
- Local stack: `docker compose up --build`.
- Test suite: `./mvnw test` on Unix-like systems or `.\mvnw.cmd test` on Windows. Integration tests require a working Docker runtime because they use PostgreSQL Testcontainers.
- The current schema is derived from JPA entities via Hibernate `ddl-auto: update`; there are no versioned migrations. Treat this as an explicit current constraint, not a recommended end state.

## Implementation Rules

- Preserve the existing controller/service/repository separation unless an accepted ADR changes it.
- Keep API contracts in DTOs rather than exposing JPA entities.
- Use UTC for persisted and API timestamps.
- Preserve stateless bearer-token authentication and method-level role checks unless intentionally superseded.
- Do not mark entity-only scaffolding as a working feature. A workflow is implemented only when its callable behavior and relevant tests exist.
- Do not change application behavior merely to match old documentation. Correct the documentation unless a separate task explicitly addresses a bug.
- Do not invent historical rationale. Use this exact statement when evidence is absent: `Reason not recoverable from repository history.`

## Mandatory Post-Development Documentation

After every meaningful feature, bug fix, refactor, schema, infrastructure, or architectural change, evaluate and update the relevant engineering-memory artifacts in the same commit or PR where practical.

### `docs/PROJECT_STATUS.md`

Update it when a feature becomes complete or partial, is removed, gains a major TODO, accumulates important technical debt, or when the current focus changes.

### `docs/CHANGELOG.md`

Record meaningful user-facing or engineering changes. Do not add noise for formatting-only or insignificant edits. Use repository-supported dates and facts only.

### `docs/ARCHITECTURE.md`

Update it whenever the current architecture, request flow, security model, integrations, testing strategy, or deployment model changes. Clearly separate current behavior from planned behavior.

### ER diagram

If any persistent model changes, update `docs/diagrams/er-diagram.mmd`. This includes tables/entities, columns, relationships, foreign keys, indexes, unique constraints, status fields, and join tables. The diagram must match entities and migrations (when migrations are introduced).

### Architecture diagram

Update `docs/diagrams/architecture.mmd` whenever runtime components or important interactions change.

### ADRs

Create an ADR for a meaningful design decision: a database or schema-management strategy, authentication change, distributed coordination mechanism, messaging/cache/storage provider, consistency strategy, major library or pattern, or domain-model redesign. ADRs explain why, consequences, and trade-offs; they are not a log of trivial code edits.

## Definition of Done

A development task is complete only when:

1. Implementation is complete.
2. Relevant tests are added or updated.
3. Appropriate tests/build checks have run where possible, with failures or environmental blockers reported.
4. Engineering-memory artifacts have been evaluated.
5. Required status, changelog, architecture, diagram, and ADR updates are included.
6. Documentation accurately describes the resulting implementation.

## Never Do This

- Invent historical reasoning or undocumented plans.
- Silently change an architectural decision.
- Leave the ER diagram inconsistent with persistent models or migrations.
- Mark incomplete features as completed.
- Describe planned architecture as implemented.
- Create ADRs for trivial changes.
- Update documentation from assumptions unsupported by code, configuration, tests, or Git history.
- Treat an earlier agent conversation as authoritative.

## Git-Native Workflow

The intended flow is implementation -> tests -> engineering-memory update -> commit/PR -> GitHub. A meaningful code commit should include the applicable project status, changelog, ADR, ER diagram, and architecture updates. Do not push automatically unless the user explicitly requests it and the repository workflow permits it.

Agent-specific files should remain lightweight and point here rather than duplicate these rules.
