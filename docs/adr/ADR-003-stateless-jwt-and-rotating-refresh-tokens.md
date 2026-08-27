# ADR-003: Stateless JWT Access and Rotating Refresh Tokens

Date: 2026-08-19  
Status: Accepted

## Context

TicketNest needed authenticated API access beyond registration. Commit `fb9cde4` introduced JWT authentication, login/logout, and persistent refresh tokens.

## Options Considered

No alternatives (such as server sessions or a third-party identity provider) are recorded.

## Decision

Use stateless HS256 JWT access tokens with user ID and role claims. Use random opaque refresh tokens, persist only SHA-256 hashes, rotate tokens on refresh, and revoke all user refresh tokens when a revoked token is reused.

## Reasoning

The code and commit message establish the selected mechanism. Reason not recoverable from repository history.

## Consequences

### Positive

- Protected requests do not require session storage or a database lookup.
- Stolen database rows do not expose usable opaque refresh tokens directly.
- Rotation limits continued use of a refresh token and provides reuse response behavior.

### Negative

- Role and active-state changes are not observed by already-issued access tokens.
- HS256 requires every token issuer/verifier to share the signing secret.
- Refresh-token rows accumulate unless cleanup is invoked.

### Risks / Trade-offs

- Secure secret management is required outside development.
- Revoking refresh tokens does not immediately invalidate existing access JWTs.
- Reuse handling revokes all refresh sessions for the user, which is protective but disruptive.

## Related

- Commit `fb9cde4`
- `AuthService`, `JwtUtil`, `JwtAuthenticationFilter`, `SecurityConfig`
- `RefreshToken` and `RefreshTokenRepository`
