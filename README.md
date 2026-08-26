# TicketNest

Ticket booking backend built with Java and Spring Boot.

## Tech Stack

* Java 21
* Spring Boot
* Spring Web
* Spring Data JPA
* PostgreSQL
* Lombok
* Jakarta Validation
* Maven

## Project Goal

TicketNest is a production-oriented ticket booking backend built as a hands-on project while learning Java, Spring Boot, system design, databases, and distributed systems.

## Development

### Run the complete stack with Docker

From a clean clone, build and start TicketNest with PostgreSQL:

```shell
docker compose up --build
```

The services are available at:

* API: `http://localhost:8080`
* Swagger UI: `http://localhost:8080/swagger`
* Health: `http://localhost:8080/actuator/health`
* PostgreSQL: `localhost:5432`

Compose includes development defaults, so an `.env` file is not required. To
override the database credentials or JWT secret, copy `.env.example` to `.env`
and edit its values. The published ports can also be overridden with
`TICKETNEST_APP_PORT` and `TICKETNEST_POSTGRES_PORT`. Do not use the development
defaults in production.

Useful commands:

```shell
# View service status and health
docker compose ps

# Follow application logs
docker compose logs -f app

# Stop the stack while preserving database data
docker compose down

# Stop the stack and remove all local TicketNest database data
docker compose down -v
```

### Run the application directly

Start PostgreSQL with `docker compose up postgres`, then run TicketNest from
IntelliJ IDEA or Maven. Direct local runs retain the HTTPS development default:

```text
https://localhost:8443
```

The Maven integration tests continue to use isolated PostgreSQL containers via
Testcontainers and do not depend on the Compose database.
