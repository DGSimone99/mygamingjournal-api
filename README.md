# 🎮 MyGamingJournal API

![CI](https://github.com/DGSimone99/mygamingjournal-api/actions/workflows/ci.yml/badge.svg)

Backend for **MyGamingJournal**, a video game journal: personal library, achievement progress, reviews,
follows, matchmaking and levels.

## Architecture

- **Spring Boot 4.1 · Java 21 · PostgreSQL · Flyway**
- The database stores **only user data**. Games and achievements are referenced by their
  [RAWG](https://rawg.io/apidocs) ids, and no catalog data is persisted.
- Game data is served through a **RAWG proxy** (`/api/games`, `/api/genres`, `/api/platforms`) with
  short-lived in-memory caches (Caffeine). The API key stays on the server.
- JWT authentication, errors as RFC 9457 `ProblemDetail`, OpenAPI documentation.

## Run locally

Prerequisites: **JDK 21** and **Docker**.

1. `cp .env.example .env` and set `RAWG_API_KEY` (free at https://rawg.io/apidocs).
   Without it the app still starts; catalog endpoints answer 503.
2. `./mvnw spring-boot:run`. The `dev` profile starts PostgreSQL from `compose.yaml` automatically.
3. Open http://localhost:8080/swagger-ui.html

## Tests

```bash
./mvnw verify
```

Integration tests run against a real PostgreSQL in Testcontainers; RAWG is always mocked.

## Configuration

| Variable | Purpose |
|---|---|
| `RAWG_API_KEY` | RAWG API key |
| `JWT_SECRET` | at least 32 bytes; required outside `dev` |
| `CLOUDINARY_URL` | avatar uploads (optional) |
| `CORS_ALLOWED_ORIGINS` | comma-separated frontend origins |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | database outside `dev` |

## Credits

Game data and images are provided by [RAWG](https://rawg.io).
