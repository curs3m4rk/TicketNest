# API versioning decision

TicketNest uses URI-based major versions. Version 1 application endpoints live
under `/api/v1`; for example:

- `POST /api/v1/auth/login`
- `GET /api/v1/shows`
- `POST /api/v1/bookings`

The migration intentionally removes the original unversioned routes:

| Previous prefix | Version 1 prefix |
| --- | --- |
| `/auth` | `/api/v1/auth` |
| `/api/shows` | `/api/v1/shows` |
| `/api/venues` | `/api/v1/venues` |
| `/api/bookings` | `/api/v1/bookings` |
| `/api/admin` | `/api/v1/admin` |

Operational endpoints keep independent paths: health remains under `/actuator`,
Swagger UI remains at `/swagger`, and the OpenAPI document remains at
`/v3/api-docs`. The `v3` there is the OpenAPI specification version rather than
the TicketNest API version.

## Why URI versioning

The version is visible in requests, logs, browser history, generated
documentation, and client configuration. It also works naturally with HTTP
caches and routing infrastructure. Header-based versioning keeps resource URLs
stable, but makes versions less visible and requires clients and API tools to
send negotiation metadata correctly.

For this API, explicit controller prefixes are enough because only one major
version exists. [Spring MVC also supports centralized API version resolution](https://docs.spring.io/spring-framework/reference/web/webmvc-versioning.html);
that becomes useful if TicketNest later needs version ranges, deprecation
headers, or several active versions.

## Evolution policy

Backward-compatible additions stay in v1. A breaking request or response
contract starts v2, with separate controller contracts where behavior differs;
versions may continue sharing service-layer business logic. TicketNest does not
provide a default version, aliases, or redirects for unversioned routes.
Authenticated requests to unversioned or unsupported application paths receive
`404 Not Found`. Without authentication, the existing security fallback may
return `401 Unauthorized` before route resolution.

Interview summary: TicketNest chose URI major versioning because it is explicit,
easy to discover and test, and simple for gateways and clients to route. The
tradeoff is that clients must change URLs when adopting a new major version.
