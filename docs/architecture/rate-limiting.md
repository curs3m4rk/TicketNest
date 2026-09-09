# Rate-limiting decision

TicketNest applies token-bucket limits before controller invocation on two
write-sensitive endpoints:

| Endpoint | Identity | Capacity and refill |
| --- | --- | --- |
| `POST /api/v1/auth/login` | Client IP address | 5 requests per minute |
| `POST /api/v1/bookings` | Authenticated user email | 10 requests per minute |

Tokens refill continuously and each bucket can hold at most its configured
capacity. Every request consumes a token, including malformed requests, failed
credentials, validation failures, conflicts, and idempotent booking retries.
Unauthenticated booking requests are rejected by Spring Security before the
booking limiter and therefore do not consume a user quota.

When a bucket has no token, the API responds with `429 Too Many Requests` using
the standard TicketNest error body. The `Retry-After` response header reports
the whole number of seconds until the next token is available. Successful
responses do not include quota headers.

## Identity and deployment tradeoffs

Login attempts use the servlet request's remote address after Spring processes
forwarded headers. Production enables `server.forward-headers-strategy:
framework`, so this relies on the deployment proxy supplying and sanitizing
forwarding information. An IP limit can affect several legitimate users behind
the same corporate or carrier NAT, but avoids letting attackers bypass the
limit by changing the submitted email address.

Booking creation uses the authenticated principal, so one user's activity does
not consume another user's quota. Login and booking keys are also namespaced by
policy and cannot affect one another.

## In-memory limitations and Redis migration

Buckets live in a thread-safe map inside one application process. Limits reset
when that process restarts, and multiple replicas each enforce an independent
quota. Inactive, fully replenished buckets are removed periodically to bound
memory use.

The HTTP interceptor depends on a small `RateLimiter` interface rather than the
in-memory implementation. A later Redis-backed implementation can atomically
store token state with expiry while retaining the current policies, identity
keys, controller behavior, and `429` response contract. Redis would provide a
single quota across replicas and naturally expire inactive keys.
