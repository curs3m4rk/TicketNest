# ADR-006: Persistent Passwordless OTP Challenges

Date: 2026-08-28  
Status: Accepted

## Context

Commit `fd95a72` added passwordless login by email or SMS while retaining the same access/refresh-token result as password login.

## Options Considered

No alternative passwordless mechanism or external identity service is recorded.

## Decision

Persist OTP challenges in PostgreSQL. Normalize destinations; store an HMAC-SHA256 code hash rather than the code; enforce expiry, resend cooldown, request-window limit, and verification-attempt limit; lock the challenge row during verification; and mark a successful challenge consumed. Deliver through conditional SMTP or Twilio adapters and issue the existing JWT/refresh-token pair on success.

## Reasoning

Security properties are directly evident in code and tests. The historical reason for choosing database-backed challenges, HMAC, SMTP, or Twilio over alternatives is not recoverable from repository history.

## Consequences

### Positive

- Plain OTP codes are not persisted.
- One-time use and concurrent verification are coordinated by database state and row locking.
- Email and SMS share one challenge workflow while delivery remains replaceable behind `OtpSender`.

### Negative

- Request and verification depend on PostgreSQL availability.
- Delivery is synchronous and adds provider latency to the request.
- Challenge rows have no cleanup process.

### Risks / Trade-offs

- Both delivery channels are disabled by default, so deployment configuration is required.
- Rate limiting is per normalized destination in the database, not per IP/device or distributed edge.
- Delivery failure deletes the challenge, but external delivery may have an ambiguous outcome after a network failure.

## Related

- Commit `fd95a72`
- `OtpService`, `OtpSender`, `EmailOtpSender`, `TwilioSmsOtpSender`
- `OtpChallenge`, `OtpChallengeRepository`
- ADR-003
