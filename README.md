# CineScout

AI production and location scouting. A filmmaker submits a scene; the platform extracts the
physical location requirements, finds real venues with grounded web search, assesses booking
friction, computes shoot logistics (light, weather, noise risk, nearby services) and drafts
outreach to venue owners.

> Work in progress. The backend is being built one step at a time; see [Status](#status).

## Stack

- Java 21, Spring Boot 3.5, Spring WebFlux
- PostgreSQL, Spring Data JPA, Flyway (Flyway owns the schema; Hibernate only validates it)
- JUnit 5, Mockito, WireMock, Testcontainers
- Planned: Spring Security, OpenAPI docs, resilience (retry / circuit breaker), a React frontend

## Layout

| Path | Contents |
|---|---|
| `backend/` | Spring Boot application (`com.cinescout`) |
| `backend/src/main/resources/db/migration/` | Flyway migrations |
| `docs/architecture.md` | Entity relationships and schema decisions |

## Running

Requires JDK 21+ and Maven. The tests start PostgreSQL through Testcontainers, so Docker
must be running.

```bash
cd backend
mvn test
DB_PASSWORD=... mvn spring-boot:run
```

Configuration comes from the environment:

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/cinescout` |
| `DB_USERNAME` | `cinescout` |
| `DB_PASSWORD` | none, must be set |
| `PORT` | `8081` |

## Status

1. Schema and design - done
2. Domain models and DTOs - done
3. External API clients (LLM and search) - next
4. Orchestration service (extract, search, score)
5. REST controllers, validation and OpenAPI docs
