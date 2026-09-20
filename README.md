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

The LLM client (IBM watsonx.ai) is only created when `WATSONX_API_KEY` is set, so the app
still starts without IBM credentials. When it is set, the other two are required:

| Variable | Default |
|---|---|
| `WATSONX_API_KEY` | none, IBM Cloud API key |
| `WATSONX_PROJECT_ID` | none, must be set with the key |
| `WATSONX_MODEL_ID` | none, must be set with the key |
| `WATSONX_URL` | `https://us-south.ml.cloud.ibm.com` |

To check the client against the real service (skipped by default, billed to your project):

```bash
WATSONX_LIVE_TEST=true WATSONX_API_KEY=... WATSONX_PROJECT_ID=... WATSONX_MODEL_ID=... \
  mvn test -Dtest=WatsonxLiveSmokeTest
```

Likewise the search client (Parallel) is only created when `PARALLEL_API_KEY` is set:

| Variable | Default |
|---|---|
| `PARALLEL_API_KEY` | none, Parallel API key |
| `PARALLEL_URL` | `https://api.parallel.ai` |

```bash
PARALLEL_LIVE_TEST=true PARALLEL_API_KEY=... mvn test -Dtest=ParallelLiveSmokeTest
```

Scouting (extract requirements, search, assess venues, save candidates) needs **both** keys, so
without them the app still starts but has no scouting beans. Optional tuning, all with defaults:

| Property | Default |
|---|---|
| `cinescout.scouting.assessment-concurrency` | `4` venues assessed by the LLM at once |
| `cinescout.resilience.max-attempts` | `3` tries per LLM or search call |
| `cinescout.resilience.initial-backoff` / `max-backoff` | `500ms` / `5s` |
| `cinescout.resilience.breaker-window-size` / `-minimum-calls` | `10` / `5` |
| `cinescout.resilience.breaker-failure-rate-percent` | `50` |
| `cinescout.resilience.breaker-open-duration` | `30s` |

## Authentication

Every endpoint needs a login except `POST /api/auth/register`. Authentication is HTTP Basic (email
and password) against the `users` table, so there is no session and no token to manage yet:

```bash
curl -X POST localhost:8081/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"ada@example.com","password":"a-long-password","displayName":"Ada"}'
curl -u ada@example.com:a-long-password localhost:8081/api/auth/me
```

Errors are RFC 9457 problems (`application/problem+json`). Set `cinescout.security.registration-open=false`
to stop new accounts being created on a deployment that should not be public. HTTP Basic sends the
password on every request, so run it behind HTTPS; tokens are the planned replacement for browsers.

## API

Once running, the interactive documentation is at `/swagger-ui.html` and the OpenAPI description at
`/v3/api-docs` (turn both off with `springdoc.api-docs.enabled=false`). Routes, all under `/api`:

| Area | Routes |
|---|---|
| Accounts | `POST /auth/register` (public), `GET /auth/me` |
| Projects | `POST /projects`, `GET /projects[?status=]`, `GET`/`PUT`/`DELETE /projects/{id}` |
| Scenes | `POST`/`GET /projects/{id}/scenes`, `GET`/`PUT`/`DELETE /scenes/{id}` |
| Locations | `POST`/`GET /scenes/{id}/locations`, `GET`/`PUT`/`DELETE /locations/{id}` |
| Scouting | `POST /scenes/{id}/parse`, `POST /scenes/{id}/scout[?maxResults=]` |

`PUT` is a full replacement. Someone else's resource is always a `404`. The two scouting routes call
paid services and can take many seconds; they answer `503` when the server has no AI keys.

## Status

1. Schema and design - done
2. Domain models and DTOs - done
3. External API clients: LLM (watsonx.ai) and search (Parallel) - implemented, live checks pending
4. Orchestration service (extract, search, assess, save) with retry and circuit breaker - done
5. REST controllers, authentication, validation and OpenAPI docs - done

Not built yet: outreach email drafting (its DTOs exist), the environmental/logistics module, and the frontend.
