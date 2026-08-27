# ADR-004: Role-Based Authorization for Catalog Mutations

Date: 2026-08-19  
Status: Accepted

## Context

The catalog exposes read and mutation operations. Commit `98dece3` introduced `USER` and `ADMIN` roles and method-level access control.

## Options Considered

No alternative permission model is recorded.

## Decision

Use the role embedded in the JWT to create Spring Security authorities. Require `ADMIN` through `@PreAuthorize` for show and venue create/update/delete methods; allow any authenticated role to read catalog data.

## Reasoning

Reason not recoverable from repository history.

## Consequences

### Positive

- Mutation policy is explicit at controller methods.
- The two-role model is simple to inspect and test.

### Negative

- Permissions cannot be more granular than the role enum.
- There is no administration API for role assignment.
- Token authorities remain stale until access-token expiry.

### Risks / Trade-offs

- New mutation endpoints must consistently add method authorization.
- A more detailed permission model will require an intentional redesign and replacement ADR.

## Related

- Commit `98dece3`
- `Role`, `SecurityConfig`, `JwtAuthenticationFilter`
- `ShowController`, `VenueController`
