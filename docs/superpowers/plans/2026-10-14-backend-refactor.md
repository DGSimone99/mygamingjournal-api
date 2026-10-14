# MyGamingJournal API v2 — Backend Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the MyGamingJournal Spring Boot backend so it stores only user data (games referenced by RAWG id), proxies RAWG with an in-memory cache, and ships clean, tested, documented code.

**Architecture:** Feature packages under `it.mygamingjournal` (auth, user, social, library, review, matchmaking, catalog, progression, media, common). PostgreSQL schema owned by Flyway, Hibernate in `validate` mode. RAWG is reached only through `RawgApi` (`@HttpExchange` on `RestClient`) wrapped by `CatalogService`, which maps RAWG JSON to our own DTOs and caches them in Caffeine. Controller behaviour is tested end-to-end through MockMvc against a real Postgres (Testcontainers) with `RawgApi` mocked; this replaces the `@WebMvcTest` slices mentioned in the spec, because full-context tests also exercise the real security chain. The only slice test is the error-handler test in Task 2.

**Tech Stack:** Java 21, Spring Boot 4.1.1 (Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3), PostgreSQL 17, Flyway, Caffeine, jjwt 0.13.0 (gson serializer), springdoc-openapi 3.1.1, Cloudinary (`cloudinary-http5` 2.5.0), Lombok, JUnit 5, AssertJ, Mockito, Testcontainers 2.0.5.

**Spec:** `docs/superpowers/specs/2026-10-14-backend-refactor-design.md`

## Global Constraints

- Base package `it.mygamingjournal`; all endpoints under `/api`; JSON in camelCase.
- No table may store RAWG catalog data (names, descriptions, images, RAWG ratings). Games and achievements are referenced only by `rawg_game_id` / `rawg_achievement_id`.
- Pagination: query `page` (from 1, default 1) and `pageSize` (default 20, max 40); response `{ items, page, pageSize, totalItems }`.
- Errors are `ProblemDetail` (RFC 9457): 400 validation (with `errors: [{field, message}]`), 401, 403, 404, 409, 502 RAWG failure, 503 RAWG not configured/unreachable.
- RAWG key only from `RAWG_API_KEY` (`rawg.api-key: ${RAWG_API_KEY:}`); never committed, never logged. Missing key: app starts, catalog endpoints return 503 "RAWG API key not configured".
- Caffeine TTLs: lists 10 min / 500 entries; details, achievements, related 12 h / 2000, 500, 500; genres and platforms 24 h / 10.
- RAWG HTTP client: connect timeout 3 s, read timeout 10 s; achievements read up to 20 pages.
- JWT HS256, secret ≥ 32 bytes from `JWT_SECRET`, expiry 7 days, `sub` = user id. BCrypt passwords, min 8 characters (max 72, BCrypt limit).
- Avatar: JPEG, PNG or WebP only, max 5 MB.
- Personal rating 0–10 (one decimal allowed), review score integer 1–10, kept separate.
- Hibernate `ddl-auto=validate`; schema changes only through Flyway migrations.
- Prerequisites to run anything: JDK 21 on `PATH`/`JAVA_HOME` and Docker running. Use `./mvnw` (no global Maven).
- Every commit message ends with the line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (shown once below as `<trailer>`; always include it).

## Review Focus

1. **Case-insensitive identity:** registering `Mario` when `mario` exists, or `MARIO@example.com` when `mario@example.com` exists, must return 409, and login must work regardless of case → tests in Task 3.
2. **RAWG list pages past the end:** RAWG answers 404 for a page beyond the last one; the catalog must return an empty page, not "Game not found" → test in Task 4.
3. **One RAWG game failing during library enrichment:** the library page must still return 200 with `game: null` for that entry → test in Task 5.
4. **Avatar with a spoofed content type:** a text file sent as `image/png` must be rejected with 400 (magic-byte check) → test in Task 9.
5. **Account deletion side effects:** deleting an account must remove the user from other users' following lists, game reviews and matchmaking results → tests in Tasks 8 and 10.

---

## File map

```
pom.xml                                   rewritten (Boot 4.1.1)
compose.yaml, .env.example, .gitignore    dev environment
Dockerfile, .github/workflows/ci.yml      Task 12
src/main/resources/application.yml
src/main/resources/db/migration/V1__init.sql
src/main/java/it/mygamingjournal/
  MyGamingJournalApplication.java
  common/ClockConfig.java
  common/error/{ApiExceptionHandler, NotFoundException, ConflictException, BadRequestException, FieldErrorItem}.java
  common/paging/{PageParams, PageResponse}.java
  auth/{SecurityConfig, JwtProperties, JwtService, JwtAuthenticationFilter, AuthUser, AuthController, AuthService,
        InvalidCredentialsException, RegisterRequest, LoginRequest, AuthResponse}.java
  user/{User, Role, GamingAccounts, UserRepository, UserSummary, GamingAccountsDto, MeResponse, PublicProfileResponse,
        UpdateProfileRequest, ChangePasswordRequest, ProfileService, UserController, AvatarService, AvatarController, AvatarResponse}.java
  catalog/{RawgProperties, RawgClientConfig, RawgApi, RawgModels, RawgMapper, CatalogService, CatalogController, GameQuery,
           GameSummary, GameDetails, Requirements, CommunityStats, GameAchievement, RelatedGames, NamedItem,
           RawgUnavailableException, RawgUpstreamException}.java
  library/{GameStatus, GameEntry, GameEntryRepository, AchievementProgress, AchievementProgressId, AchievementProgressRepository,
           PlayAvailability, PlayAvailabilityRepository, LibraryService, LibraryController, LibrarySort, UpsertEntryRequest,
           LibraryEntryResponse, AvailabilityResponse, LibraryStatsService, UserStats, AchievementService, AchievementController,
           AchievementProgressResponse, AvailabilityService, AvailabilityRequest}.java
  progression/{LevelingSystem, ProgressionService}.java
  social/{Follow, FollowId, FollowRepository, FollowService, SocialController}.java
  review/{Review, ReviewRepository, ReviewService, ReviewController, ReviewRequest, ReviewResponse, CommunityStatsService}.java
  matchmaking/{MatchmakingService, MatchmakingController, AvailablePlayerResponse}.java
  media/{ImageStorage, CloudinaryImageStorage, ImageSignatures, MediaUnavailableException}.java
  config/OpenApiConfig.java
src/test/java/it/mygamingjournal/
  TestcontainersConfiguration.java, IntegrationTest.java, ApplicationContextTest.java
  common/error/{ErrorTestController, ApiExceptionHandlerTest}.java
  auth/{JwtServiceTest, AuthIntegrationTest}.java
  catalog/{RawgFixtures, RawgMapperTest, CatalogServiceTest, CatalogIntegrationTest}.java
  library/{LibraryIntegrationTest, AchievementIntegrationTest}.java
  progression/LevelingSystemTest.java
  social/SocialIntegrationTest.java
  user/{ProfileIntegrationTest, AvatarIntegrationTest}.java
  media/{ImageSignaturesTest, CloudinaryImageStorageTest}.java
  review/ReviewIntegrationTest.java
  matchmaking/MatchmakingIntegrationTest.java
  config/OpenApiIntegrationTest.java
```

Repository design note: entities reference other aggregates by plain id columns (`Long userId`, `long rawgGameId`), not JPA associations. This keeps lazy-loading out of the picture (`open-in-view` is off) and mirrors the database cascades, which do the cleanup.

---

### Task 1: Project skeleton, schema and test harness

Replace the v1 code with an empty Boot 4.1 application whose Flyway migration creates the whole v2 schema.

**Files:**
- Delete: `src/main/java/it/MyGamingJournal/` (whole tree), `src/test/java/it/MyGamingJournal/` (whole tree), `src/main/resources/application-sample.properties`
- Rewrite: `pom.xml`, `.gitignore`
- Create: `src/main/java/it/mygamingjournal/MyGamingJournalApplication.java`, `src/main/resources/application.yml`, `src/main/resources/db/migration/V1__init.sql`, `compose.yaml`, `.env.example`
- Test: `src/test/java/it/mygamingjournal/TestcontainersConfiguration.java`, `src/test/java/it/mygamingjournal/IntegrationTest.java`, `src/test/java/it/mygamingjournal/ApplicationContextTest.java`

**Interfaces:**
- Produces: `IntegrationTest` (abstract base: `mockMvc`, `jdbcTemplate`, truncates all data after each test); `TestcontainersConfiguration`; profiles `dev` (default) and `test`.

- [ ] **Step 1: Remove the v1 sources**

```bash
git rm -r -q src/main/java/it/MyGamingJournal src/test/java/it/MyGamingJournal src/main/resources/application-sample.properties
```

- [ ] **Step 2: Rewrite `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.1</version>
        <relativePath/>
    </parent>

    <groupId>it.mygamingjournal</groupId>
    <artifactId>mygamingjournal-api</artifactId>
    <version>2.0.0-SNAPSHOT</version>
    <name>MyGamingJournal API</name>
    <description>Backend for MyGamingJournal: user data, community features and a cached RAWG proxy</description>

    <properties>
        <java.version>21</java.version>
        <springdoc.version>3.1.1</springdoc.version>
        <jjwt.version>0.13.0</jjwt.version>
        <cloudinary.version>2.5.0</cloudinary.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-flyway</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-restclient</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>com.github.ben-manes.caffeine</groupId>
            <artifactId>caffeine</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>${springdoc.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-gson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>com.cloudinary</groupId>
            <artifactId>cloudinary-http5</artifactId>
            <version>${cloudinary.version}</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-docker-compose</artifactId>
            <scope>runtime</scope>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-postgresql</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>mygamingjournal-api</finalName>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: Rewrite `.gitignore`** (the v1 file ignored `application.yml`, which must now be committed)

```gitignore
target/
HELP.md

### IDEs ###
.idea/
*.iml
*.iws
*.ipr
.vscode/
.classpath
.factorypath
.project
.settings/

### Logs ###
*.log
logs/

### OS ###
.DS_Store

### Secrets: copy .env.example to .env ###
.env
.env.local
```

- [ ] **Step 4: Write the test harness**

`src/test/java/it/mygamingjournal/TestcontainersConfiguration.java`
```java
package it.mygamingjournal;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }
}
```

`src/test/java/it/mygamingjournal/IntegrationTest.java`
```java
package it.mygamingjournal;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanDatabase() {
        // Every user-owned table references users, so the cascade empties them all.
        jdbcTemplate.execute("TRUNCATE users RESTART IDENTITY CASCADE");
    }
}
```

`src/test/java/it/mygamingjournal/ApplicationContextTest.java`
```java
package it.mygamingjournal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationContextTest extends IntegrationTest {

    @Test
    void flywayCreatesTheV2Schema() {
        List<String> tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);

        assertThat(tables).contains("users", "user_roles", "user_languages", "user_gaming_accounts",
                "user_follows", "game_entries", "achievement_progress", "play_availability", "reviews",
                "flyway_schema_history");
        assertThat(tables).doesNotContain("games", "achievements", "related_games");
    }
}
```

- [ ] **Step 5: Run the test to verify it fails**

Run: `./mvnw test -Dtest=ApplicationContextTest`
Expected: FAIL with "Unable to find a @SpringBootConfiguration", because the application class does not exist yet.

- [ ] **Step 6: Create the application class, configuration and migration**

`src/main/java/it/mygamingjournal/MyGamingJournalApplication.java`
```java
package it.mygamingjournal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MyGamingJournalApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyGamingJournalApplication.class, args);
    }
}
```

`src/main/resources/application.yml`
```yaml
spring:
  application:
    name: mygamingjournal-api
  config:
    import: optional:file:.env[.properties]
  profiles:
    default: dev
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5433/mygamingjournal}
    username: ${DB_USERNAME:mgj}
    password: ${DB_PASSWORD:mgj}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 6MB
  docker:
    compose:
      enabled: false

app:
  jwt:
    secret: ${JWT_SECRET:}
    expiration: 7d
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:5173}
  cloudinary:
    url: ${CLOUDINARY_URL:}

rawg:
  base-url: https://api.rawg.io/api
  api-key: ${RAWG_API_KEY:}

springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false

---
spring:
  config:
    activate:
      on-profile: dev
  docker:
    compose:
      enabled: true
      lifecycle-management: start-only
app:
  jwt:
    secret: ${JWT_SECRET:dev-only-secret-dev-only-secret-dev-only}
springdoc:
  api-docs:
    enabled: true
  swagger-ui:
    enabled: true

---
spring:
  config:
    activate:
      on-profile: test
app:
  jwt:
    secret: test-secret-test-secret-test-secret-0123
rawg:
  api-key: test-key

---
spring:
  config:
    activate:
      on-profile: prod
server:
  forward-headers-strategy: framework
```

`src/main/resources/db/migration/V1__init.sql`
```sql
CREATE TABLE users (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    username      VARCHAR(30)  NOT NULL,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(50)  NOT NULL,
    avatar_url    VARCHAR(512),
    bio           TEXT,
    experience    INTEGER      NOT NULL DEFAULT 0,
    level         INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL
);
CREATE UNIQUE INDEX ux_users_username_ci ON users (lower(username));
CREATE UNIQUE INDEX ux_users_email_ci ON users (lower(email));

CREATE TABLE user_roles (
    user_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE TABLE user_languages (
    user_id       BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    language_code VARCHAR(10) NOT NULL,
    PRIMARY KEY (user_id, language_code)
);

CREATE TABLE user_gaming_accounts (
    user_id   BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    steam     VARCHAR(100),
    psn       VARCHAR(100),
    xbox      VARCHAR(100),
    nintendo  VARCHAR(100),
    epic      VARCHAR(100),
    riot      VARCHAR(100),
    discord   VARCHAR(100)
);

CREATE TABLE user_follows (
    follower_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    followed_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (follower_id, followed_id),
    CHECK (follower_id <> followed_id)
);
CREATE INDEX ix_user_follows_followed ON user_follows (followed_id);

CREATE TABLE game_entries (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id         BIGINT           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    rawg_game_id    BIGINT           NOT NULL,
    status          VARCHAR(20)      NOT NULL CHECK (status IN ('WISHLIST', 'PLAYING', 'COMPLETED', 'DROPPED', 'BACKLOG')),
    personal_rating DOUBLE PRECISION CHECK (personal_rating BETWEEN 0 AND 10),
    hours_played    DOUBLE PRECISION NOT NULL DEFAULT 0 CHECK (hours_played >= 0),
    notes           TEXT             NOT NULL DEFAULT '',
    added_at        TIMESTAMPTZ      NOT NULL,
    updated_at      TIMESTAMPTZ      NOT NULL,
    UNIQUE (user_id, rawg_game_id)
);
CREATE INDEX ix_game_entries_rawg_game ON game_entries (rawg_game_id);

CREATE TABLE achievement_progress (
    entry_id            BIGINT      NOT NULL REFERENCES game_entries (id) ON DELETE CASCADE,
    rawg_achievement_id BIGINT      NOT NULL,
    unlocked_at         TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (entry_id, rawg_achievement_id)
);

CREATE TABLE play_availability (
    entry_id        BIGINT PRIMARY KEY REFERENCES game_entries (id) ON DELETE CASCADE,
    platforms       TEXT[] NOT NULL,
    languages       TEXT[] NOT NULL,
    available_until DATE
);

CREATE TABLE reviews (
    id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    rawg_game_id BIGINT      NOT NULL,
    text         TEXT        NOT NULL,
    score        INTEGER     NOT NULL CHECK (score BETWEEN 1 AND 10),
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, rawg_game_id)
);
CREATE INDEX ix_reviews_rawg_game ON reviews (rawg_game_id);
```

`compose.yaml`
```yaml
services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: mygamingjournal
      POSTGRES_USER: mgj
      POSTGRES_PASSWORD: mgj
    ports:
      - "5433:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
```

`.env.example`
```properties
# Copy this file to .env (git-ignored) and fill in the values you need.

# RAWG API key, get one at https://rawg.io/apidocs. Empty = catalog endpoints answer 503.
RAWG_API_KEY=

# At least 32 random bytes. Optional in the dev profile, required elsewhere.
JWT_SECRET=

# cloudinary://<api_key>:<api_secret>@<cloud_name>. Empty = avatar upload answers 503.
CLOUDINARY_URL=

# Comma-separated list of frontend origins.
CORS_ALLOWED_ORIGINS=http://localhost:5173

# Only needed outside the dev profile (dev starts compose.yaml automatically).
DB_URL=jdbc:postgresql://localhost:5433/mygamingjournal
DB_USERNAME=mgj
DB_PASSWORD=mgj
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `./mvnw test -Dtest=ApplicationContextTest`
Expected: PASS (Testcontainers pulls `postgres:17-alpine` on first run).

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "chore: replace v1 sources with Boot 4.1 skeleton and v2 schema

<trailer>"
```

---

### Task 2: Common error handling, paging and clock

**Files:**
- Create: `src/main/java/it/mygamingjournal/common/error/{NotFoundException,ConflictException,BadRequestException,FieldErrorItem,ApiExceptionHandler}.java`, `src/main/java/it/mygamingjournal/common/paging/{PageParams,PageResponse}.java`, `src/main/java/it/mygamingjournal/common/ClockConfig.java`
- Test: `src/test/java/it/mygamingjournal/common/error/ErrorTestController.java`, `src/test/java/it/mygamingjournal/common/error/ApiExceptionHandlerTest.java`

**Interfaces:**
- Produces:
  - `NotFoundException(String)`, `ConflictException(String)`, `BadRequestException(String)`, all `RuntimeException` → 404 / 409 / 400.
  - `record PageParams(Integer page, Integer pageSize)`: defaults 1/20, `int page()`, `int pageSize()`, `Pageable toPageable(Sort sort)`. Bind with `@Valid PageParams paging`.
  - `record PageResponse<T>(List<T> items, int page, int pageSize, long totalItems)` with `static <E, T> PageResponse<T> of(Page<E> page, PageParams params, Function<E, T> mapper)`.
  - `ApiExceptionHandler` (later tasks add handlers to it).
  - `Clock` bean (UTC).

- [ ] **Step 1: Write the failing test**

`src/test/java/it/mygamingjournal/common/error/ErrorTestController.java`
```java
package it.mygamingjournal.common.error;

import it.mygamingjournal.common.paging.PageParams;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test")
class ErrorTestController {

    record Body(@NotBlank String name) {
    }

    @GetMapping("/not-found")
    void notFound() {
        throw new NotFoundException("Thing not found");
    }

    @GetMapping("/conflict")
    void conflict() {
        throw new ConflictException("Thing already exists");
    }

    @GetMapping("/bad-request")
    void badRequest() {
        throw new BadRequestException("Thing is wrong");
    }

    @PostMapping("/body")
    void body(@Valid @RequestBody Body body) {
    }

    @GetMapping("/paged")
    PageParams paged(@Valid PageParams paging) {
        return paging;
    }
}
```

`src/test/java/it/mygamingjournal/common/error/ApiExceptionHandlerTest.java`
```java
package it.mygamingjournal.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ErrorTestController.class)
@AutoConfigureMockMvc(addFilters = false)
class ApiExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void notFoundBecomesProblemDetail404() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Thing not found"));
    }

    @Test
    void conflictBecomes409() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Thing already exists"));
    }

    @Test
    void badRequestBecomes400() throws Exception {
        mockMvc.perform(get("/test/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Thing is wrong"));
    }

    @Test
    void invalidBodyListsFieldErrors() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void pagingDefaultsToFirstPageOfTwenty() throws Exception {
        mockMvc.perform(get("/test/paged"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(20));
    }

    @Test
    void pageSizeAboveFortyIsRejected() throws Exception {
        mockMvc.perform(get("/test/paged").param("pageSize", "41"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("pageSize"));
    }

    @Test
    void pageZeroIsRejected() throws Exception {
        mockMvc.perform(get("/test/paged").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("page"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=ApiExceptionHandlerTest`
Expected: FAIL (compilation: `NotFoundException`, `PageParams` … not found).

- [ ] **Step 3: Implement**

`common/error/NotFoundException.java`
```java
package it.mygamingjournal.common.error;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
```

`common/error/ConflictException.java`
```java
package it.mygamingjournal.common.error;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
```

`common/error/BadRequestException.java`
```java
package it.mygamingjournal.common.error;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
```

`common/error/FieldErrorItem.java`
```java
package it.mygamingjournal.common.error;

public record FieldErrorItem(String field, String message) {
}
```

`common/error/ApiExceptionHandler.java`
```java
package it.mygamingjournal.common.error;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    ProblemDetail handleBadRequest(BadRequestException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        // Unique constraints racing past the service-level checks.
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Resource already exists");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorItem> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorItem(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorItem> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldErrorItem(
                                error instanceof FieldError fieldError
                                        ? fieldError.getField()
                                        : result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return ResponseEntity.badRequest().body(validationProblem(errors));
    }

    private static ProblemDetail validationProblem(List<FieldErrorItem> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }
}
```

`common/paging/PageParams.java`
```java
package it.mygamingjournal.common.paging;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public record PageParams(@Min(1) Integer page, @Min(1) @Max(40) Integer pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 20;

    public PageParams {
        if (page == null) {
            page = 1;
        }
        if (pageSize == null) {
            pageSize = DEFAULT_PAGE_SIZE;
        }
    }

    public Pageable toPageable(Sort sort) {
        return PageRequest.of(page - 1, pageSize, sort);
    }

    public long offset() {
        return (long) (page - 1) * pageSize;
    }
}
```

`common/paging/PageResponse.java`
```java
package it.mygamingjournal.common.paging;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> items, int page, int pageSize, long totalItems) {

    public static <E, T> PageResponse<T> of(Page<E> page, PageParams params, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                params.page(), params.pageSize(), page.getTotalElements());
    }
}
```

`common/ClockConfig.java`
```java
package it.mygamingjournal.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw test -Dtest=ApiExceptionHandlerTest,ApplicationContextTest`
Expected: PASS (7 + 1 tests).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add ProblemDetail error handling, paging and clock

<trailer>"
```

---

### Task 3: Users, JWT authentication and security rules

**Files:**
- Create: `user/{Role,GamingAccounts,User,UserRepository,UserSummary}.java`, `auth/{JwtProperties,JwtService,AuthUser,JwtAuthenticationFilter,SecurityConfig,InvalidCredentialsException,RegisterRequest,LoginRequest,AuthResponse,AuthService,AuthController}.java`
- Modify: `common/error/ApiExceptionHandler.java` (401 handler), `src/test/java/it/mygamingjournal/IntegrationTest.java` (register helper)
- Test: `src/test/java/it/mygamingjournal/auth/JwtServiceTest.java`, `src/test/java/it/mygamingjournal/auth/AuthIntegrationTest.java`

**Interfaces:**
- Consumes: `ConflictException`, `ApiExceptionHandler`, `Clock`.
- Produces:
  - `User` entity (getters/setters: `getId()`, `getUsername()`, `getEmail()`, `getPasswordHash()`, `getDisplayName()`, `getAvatarUrl()`, `getBio()`, `getLanguages(): Set<String>`, `getGamingAccounts(): GamingAccounts`, `getExperience()`, `getLevel()`, `getCreatedAt()`); constructor `User(String username, String email, String passwordHash, Instant createdAt)`.
  - `UserRepository` with `findByUsernameIgnoreCase`, `findByEmailIgnoreCase`, `existsByUsernameIgnoreCase`, `existsByEmailIgnoreCase`, `findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(String, String, Pageable)`.
  - `record UserSummary(long id, String username, String displayName, String avatarUrl, int level)` + `static UserSummary from(User)`.
  - `record AuthUser(long id)`: the principal; controllers use `@AuthenticationPrincipal AuthUser me` (null when anonymous).
  - `JwtService.generateToken(long userId): String`, `JwtService.parseUserId(String token): Optional<Long>`.
  - Test helper in `IntegrationTest`: `TestUser register(String username)` (email `<username>@example.com`, password `password123`), `static String bearer(TestUser)`, `record TestUser(long id, String token)`.

- [ ] **Step 1: Write the failing tests**

`src/test/java/it/mygamingjournal/auth/JwtServiceTest.java`
```java
package it.mygamingjournal.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-012345";
    private static final Instant NOW = Instant.parse("2026-10-14T10:00:00Z");

    private JwtService serviceAt(Instant instant) {
        return new JwtService(new JwtProperties(SECRET, Duration.ofDays(7)), Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void tokenRoundTripsTheUserId() {
        JwtService service = serviceAt(NOW);
        assertThat(service.parseUserId(service.generateToken(42L))).contains(42L);
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtService service = serviceAt(NOW);
        String token = service.generateToken(42L);
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        assertThat(service.parseUserId(tampered)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        String token = serviceAt(NOW).generateToken(42L);
        assertThat(serviceAt(NOW.plus(Duration.ofDays(8))).parseUserId(token)).isEmpty();
    }

    @Test
    void garbageIsRejected() {
        assertThat(serviceAt(NOW).parseUserId("not-a-jwt")).isEmpty();
    }

    @Test
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("too-short", Duration.ofDays(7)), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
```

Add the helper to `src/test/java/it/mygamingjournal/IntegrationTest.java` (full file after the change):
```java
package it.mygamingjournal;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanDatabase() {
        // Every user-owned table references users, so the cascade empties them all.
        jdbcTemplate.execute("TRUNCATE users RESTART IDENTITY CASCADE");
    }

    protected TestUser register(String username) throws Exception {
        String body = """
                {"username":"%s","email":"%s@example.com","password":"password123"}
                """.formatted(username, username);
        String json = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(json, "$.user.id");
        String token = JsonPath.read(json, "$.accessToken");
        return new TestUser(id.longValue(), token);
    }

    protected static String bearer(TestUser user) {
        return "Bearer " + user.token();
    }

    public record TestUser(long id, String token) {
    }
}
```

`src/test/java/it/mygamingjournal/auth/AuthIntegrationTest.java`
```java
package it.mygamingjournal.auth;

import it.mygamingjournal.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTest {

    private static String registerBody(String username, String email) {
        return """
                {"username":"%s","email":"%s","password":"password123"}
                """.formatted(username, email);
    }

    private static String loginBody(String usernameOrEmail, String password) {
        return """
                {"usernameOrEmail":"%s","password":"%s"}
                """.formatted(usernameOrEmail, password);
    }

    @Test
    void registerReturnsTokenAndUser() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("mario", "mario@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value(not(emptyString())))
                .andExpect(jsonPath("$.user.username").value("mario"))
                .andExpect(jsonPath("$.user.displayName").value("mario"))
                .andExpect(jsonPath("$.user.level").value(0));
    }

    @Test
    void usernameIsUniqueIgnoringCase() throws Exception {
        register("mario");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("Mario", "other@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Username already taken"));
    }

    @Test
    void emailIsUniqueIgnoringCase() throws Exception {
        register("mario");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("luigi", "MARIO@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email already registered"));
    }

    @Test
    void invalidRegistrationListsEveryBadField() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a\",\"email\":\"nope\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(3)));
    }

    @Test
    void loginAcceptsUsernameOrEmailInAnyCase() throws Exception {
        register("mario");
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("MARIO", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("mario"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("Mario@Example.com", "password123")))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        register("mario");
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("mario", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }

    @Test
    void protectedEndpointWithoutTokenIs401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Authentication required"));
    }

    @Test
    void protectedEndpointWithBadTokenIs401() throws Exception {
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -Dtest=JwtServiceTest,AuthIntegrationTest`
Expected: FAIL (compilation: `JwtService`, `JwtProperties` not found).

- [ ] **Step 3: Implement the user model**

`user/Role.java`
```java
package it.mygamingjournal.user;

public enum Role {
    USER, ADMIN
}
```

`user/GamingAccounts.java`
```java
package it.mygamingjournal.user;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GamingAccounts {

    @Column(table = "user_gaming_accounts", length = 100)
    private String steam;

    @Column(table = "user_gaming_accounts", length = 100)
    private String psn;

    @Column(table = "user_gaming_accounts", length = 100)
    private String xbox;

    @Column(table = "user_gaming_accounts", length = 100)
    private String nintendo;

    @Column(table = "user_gaming_accounts", length = 100)
    private String epic;

    @Column(table = "user_gaming_accounts", length = 100)
    private String riot;

    @Column(table = "user_gaming_accounts", length = 100)
    private String discord;
}
```

`user/User.java`
```java
package it.mygamingjournal.user;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@SecondaryTable(name = "user_gaming_accounts", pkJoinColumns = @PrimaryKeyJoinColumn(name = "user_id"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String username;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    private String bio;

    @Column(nullable = false)
    private int experience;

    @Column(nullable = false)
    private int level;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20)
    private Set<Role> roles = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_languages", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "language_code", length = 10)
    @OrderBy
    private Set<String> languages = new HashSet<>();

    @Embedded
    private GamingAccounts gamingAccounts = new GamingAccounts();

    public User(String username, String email, String passwordHash, Instant createdAt) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = username;
        this.createdAt = createdAt;
        this.roles.add(Role.USER);
    }

    public GamingAccounts getGamingAccounts() {
        // Hibernate loads an all-null embeddable as null.
        if (gamingAccounts == null) {
            gamingAccounts = new GamingAccounts();
        }
        return gamingAccounts;
    }
}
```

`user/UserRepository.java`
```java
package it.mygamingjournal.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    Page<User> findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
            String username, String displayName, Pageable pageable);
}
```

`user/UserSummary.java`
```java
package it.mygamingjournal.user;

public record UserSummary(long id, String username, String displayName, String avatarUrl, int level) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getAvatarUrl(), user.getLevel());
    }
}
```

- [ ] **Step 4: Implement JWT and security**

`auth/JwtProperties.java`
```java
package it.mygamingjournal.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration expiration) {
}
```

`auth/JwtService.java`
```java
package it.mygamingjournal.auth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final Duration expiration;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        byte[] secret = properties.secret() == null ? new byte[0] : properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.jwt.secret (JWT_SECRET) must be at least 32 bytes (256 bits)");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.expiration = properties.expiration();
        this.clock = clock;
    }

    public String generateToken(long userId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(Long.toString(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    public Optional<Long> parseUserId(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Optional.of(Long.parseLong(subject));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
```

`auth/AuthUser.java`
```java
package it.mygamingjournal.auth;

/** Principal stored in the SecurityContext for authenticated requests. */
public record AuthUser(long id) {
}
```

`auth/JwtAuthenticationFilter.java`
```java
package it.mygamingjournal.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates requests carrying a valid bearer token. Invalid tokens are ignored:
 * the request continues as anonymous and protected endpoints answer 401.
 * Not a Spring bean on purpose, so Boot does not register it a second time as a servlet filter.
 */
class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(PREFIX)) {
            jwtService.parseUserId(header.substring(PREFIX.length())).ifPresent(userId -> {
                var authentication = new UsernamePasswordAuthenticationToken(
                        new AuthUser(userId), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        chain.doFilter(request, response);
    }
}
```

`auth/SecurityConfig.java`
```java
package it.mygamingjournal.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource(allowedOrigins)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/games/*/available-players").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/games/**", "/api/genres", "/api/platforms",
                                "/api/users", "/api/users/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) ->
                                writeProblem(response, HttpStatus.UNAUTHORIZED, "Authentication required"))
                        .accessDeniedHandler((request, response, ex) ->
                                writeProblem(response, HttpStatus.FORBIDDEN, "Access denied")))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static CorsConfigurationSource corsConfigurationSource(List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private static void writeProblem(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s"}"""
                .formatted(status.getReasonPhrase(), status.value(), detail));
    }
}
```

- [ ] **Step 5: Implement registration and login**

`auth/InvalidCredentialsException.java`
```java
package it.mygamingjournal.auth;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}
```

`auth/RegisterRequest.java`
```java
package it.mygamingjournal.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 30)
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "may contain only letters, digits and underscores")
        String username,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password) {
}
```

`auth/LoginRequest.java`
```java
package it.mygamingjournal.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String usernameOrEmail, @NotBlank String password) {
}
```

`auth/AuthResponse.java`
```java
package it.mygamingjournal.auth;

import it.mygamingjournal.user.UserSummary;

public record AuthResponse(String accessToken, UserSummary user) {
}
```

`auth/AuthService.java`
```java
package it.mygamingjournal.auth;

import it.mygamingjournal.common.error.ConflictException;
import it.mygamingjournal.user.User;
import it.mygamingjournal.user.UserRepository;
import it.mygamingjournal.user.UserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("Username already taken");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email already registered");
        }
        User user = users.save(new User(username, email, passwordEncoder.encode(request.password()), clock.instant()));
        return new AuthResponse(jwtService.generateToken(user.getId()), UserSummary.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String identifier = request.usernameOrEmail().trim();
        // Usernames cannot contain '@', so the identifier type is unambiguous.
        Optional<User> user = identifier.contains("@")
                ? users.findByEmailIgnoreCase(identifier)
                : users.findByUsernameIgnoreCase(identifier);
        return user
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .map(found -> new AuthResponse(jwtService.generateToken(found.getId()), UserSummary.from(found)))
                .orElseThrow(InvalidCredentialsException::new);
    }
}
```

`auth/AuthController.java`
```java
package it.mygamingjournal.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
```

Add to `ApiExceptionHandler` (with the import `it.mygamingjournal.auth.InvalidCredentialsException`):
```java
    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./mvnw test`
Expected: PASS (all tests so far, including `ApiExceptionHandlerTest`, which still loads because `SecurityConfig` is a `@Configuration` and is not part of the `@WebMvcTest` slice).

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: add users, JWT authentication and security rules

<trailer>"
```

---

### Task 4: RAWG catalog proxy with cache

**Files:**
- Create: `catalog/{RawgProperties,RawgModels,RawgApi,RawgClientConfig,NamedItem,GameSummary,Requirements,CommunityStats,GameDetails,GameAchievement,RelatedGames,RawgMapper,RawgUnavailableException,RawgUpstreamException,GameQuery,CatalogService,CatalogController}.java`
- Modify: `common/error/ApiExceptionHandler.java` (502/503), `src/test/java/it/mygamingjournal/IntegrationTest.java` (mock `RawgApi`, clear caches)
- Test: `src/test/java/it/mygamingjournal/catalog/{RawgFixtures,RawgMapperTest,CatalogServiceTest,CatalogIntegrationTest}.java`

**Interfaces:**
- Consumes: `PageParams`, `PageResponse`, `NotFoundException`.
- Produces:
  - DTOs: `GameSummary(long id, String slug, String name, LocalDate released, String coverImage, Double rating, Integer metacritic, List<String> platforms, List<String> genres)`; `GameDetails(..., CommunityStats community)` with `GameDetails withCommunity(CommunityStats)`; `CommunityStats(Double averageScore, long reviewCount, long libraryCount)` + `CommunityStats.EMPTY`; `GameAchievement(long id, String name, String description, String image, double percent)`; `RelatedGames(List<GameSummary> series, additions, parents)`; `NamedItem(long id, String name, String slug)`.
  - `CatalogService`: `PageResponse<GameSummary> searchGames(GameQuery, PageParams)`, `GameDetails getGame(long)` (throws `NotFoundException`), `List<GameAchievement> getAchievements(long)`, `RelatedGames getRelated(long)`, `List<NamedItem> getGenres()`, `List<NamedItem> getPlatforms()`, `Map<Long, GameSummary> getSummaries(Collection<Long>)` (never throws; missing games are absent), `void clearCaches()`.
  - `RawgUnavailableException` → 503, `RawgUpstreamException` → 502.
  - Test fixtures: `RawgFixtures.gameDetails(id, name)`, `game(id, name)`, `page(items, next)`, `screenshots(id)`, `achievement(id, name)`, `stubGame(rawgApi, id, name)`, `stubAchievements(rawgApi, gameId, achievements...)`.
  - `IntegrationTest.rawgApi` (Mockito mock, reset after each test).

- [ ] **Step 1: Write the failing tests**

`src/test/java/it/mygamingjournal/catalog/RawgFixtures.java`
```java
package it.mygamingjournal.catalog;

import it.mygamingjournal.catalog.RawgModels.RawgAchievement;
import it.mygamingjournal.catalog.RawgModels.RawgGame;
import it.mygamingjournal.catalog.RawgModels.RawgGameDetails;
import it.mygamingjournal.catalog.RawgModels.RawgNamed;
import it.mygamingjournal.catalog.RawgModels.RawgPage;
import it.mygamingjournal.catalog.RawgModels.RawgParentPlatform;
import it.mygamingjournal.catalog.RawgModels.RawgPlatformEntry;
import it.mygamingjournal.catalog.RawgModels.RawgRequirements;
import it.mygamingjournal.catalog.RawgModels.RawgScreenshot;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public final class RawgFixtures {

    private RawgFixtures() {
    }

    public static RawgGameDetails gameDetails(long id, String name) {
        return new RawgGameDetails(id, "game-" + id, name, "Description of " + name, "2023-08-03",
                "https://media.rawg.io/cover-" + id + ".jpg", 4.5, 96,
                new RawgNamed(4, "Mature", "mature"), "https://reddit.com/r/game" + id, 2,
                List.of(new RawgNamed(5, "RPG", "role-playing-games-rpg")),
                List.of(new RawgParentPlatform(new RawgNamed(1, "PC", "pc"))),
                List.of(new RawgPlatformEntry(new RawgNamed(4, "PC", "pc"), new RawgRequirements("min specs", "rec specs"))),
                List.of(new RawgNamed(10, "Larian Studios", "larian-studios")),
                List.of(new RawgNamed(10, "Larian Studios", "larian-studios")),
                List.of(new RawgNamed(31, "Singleplayer", "singleplayer")));
    }

    public static RawgGame game(long id, String name) {
        return new RawgGame(id, "game-" + id, name, "2023-08-03", "https://media.rawg.io/cover-" + id + ".jpg", 4.5, 96,
                List.of(new RawgParentPlatform(new RawgNamed(1, "PC", "pc"))),
                List.of(new RawgNamed(5, "RPG", "role-playing-games-rpg")));
    }

    public static <T> RawgPage<T> page(List<T> items, String next) {
        return new RawgPage<>(items.size(), next, items);
    }

    public static RawgPage<RawgScreenshot> screenshots(long gameId) {
        return page(List.of(new RawgScreenshot(1, "https://media.rawg.io/shot-" + gameId + ".jpg")), null);
    }

    public static RawgAchievement achievement(long id, String name) {
        return new RawgAchievement(id, name, name + " description", "https://media.rawg.io/ach-" + id + ".jpg", "12.50");
    }

    public static void stubGame(RawgApi rawg, long id, String name) {
        when(rawg.getGame(id)).thenReturn(gameDetails(id, name));
        when(rawg.getScreenshots(id)).thenReturn(screenshots(id));
    }

    public static void stubAchievements(RawgApi rawg, long gameId, RawgAchievement... achievements) {
        when(rawg.getAchievements(eq(gameId), eq(1), anyInt())).thenReturn(page(List.of(achievements), null));
    }
}
```

`src/test/java/it/mygamingjournal/catalog/RawgMapperTest.java`
```java
package it.mygamingjournal.catalog;

import it.mygamingjournal.catalog.RawgModels.RawgAchievement;
import it.mygamingjournal.catalog.RawgModels.RawgGame;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RawgMapperTest {

    @Test
    void mapsDetailsIntoOurShape() {
        GameDetails details = RawgMapper.toDetails(RawgFixtures.gameDetails(3328, "Baldur's Gate 3"),
                List.of("https://media.rawg.io/shot.jpg"));

        assertThat(details.name()).isEqualTo("Baldur's Gate 3");
        assertThat(details.released()).isEqualTo(LocalDate.of(2023, 8, 3));
        assertThat(details.coverImage()).isEqualTo("https://media.rawg.io/cover-3328.jpg");
        assertThat(details.platforms()).containsExactly("pc");
        assertThat(details.genres()).containsExactly("RPG");
        assertThat(details.esrbRating()).isEqualTo("Mature");
        assertThat(details.requirements()).isEqualTo(new Requirements("min specs", "rec specs"));
        assertThat(details.screenshots()).containsExactly("https://media.rawg.io/shot.jpg");
        assertThat(details.community()).isEqualTo(CommunityStats.EMPTY);
    }

    @Test
    void toleratesMissingOptionalFields() {
        RawgGame sparse = new RawgGame(1, "x", "X", "", null, null, null, null, null);

        GameSummary summary = RawgMapper.toSummary(sparse);

        assertThat(summary.released()).isNull();
        assertThat(summary.coverImage()).isNull();
        assertThat(summary.platforms()).isEmpty();
        assertThat(summary.genres()).isEmpty();
    }

    @Test
    void parsesAchievementPercentAndFallsBackToZero() {
        assertThat(RawgMapper.toAchievement(new RawgAchievement(1, "A", "d", "i", "37.25")).percent()).isEqualTo(37.25);
        assertThat(RawgMapper.toAchievement(new RawgAchievement(2, "B", "d", "i", "n/a")).percent()).isZero();
        assertThat(RawgMapper.toAchievement(new RawgAchievement(3, "C", "d", "i", null)).percent()).isZero();
    }
}
```

`src/test/java/it/mygamingjournal/catalog/CatalogServiceTest.java`
```java
package it.mygamingjournal.catalog;

import it.mygamingjournal.common.error.NotFoundException;
import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CatalogServiceTest {

    private static final String BASE = "https://rawg.test/api";

    private static final String GAME_JSON = """
            {"id":3328,"slug":"bg3","name":"Baldur's Gate 3","description_raw":"Party up","released":"2023-08-03",
             "background_image":"https://media.rawg.io/bg3.jpg","rating":4.5,"metacritic":96,
             "esrb_rating":{"id":4,"name":"Mature","slug":"mature"},"reddit_url":"","achievements_count":54,
             "genres":[{"id":5,"name":"RPG","slug":"role-playing-games-rpg"}],
             "parent_platforms":[{"platform":{"id":1,"name":"PC","slug":"pc"}}],
             "platforms":[{"platform":{"id":4,"name":"PC","slug":"pc"},"requirements":{"minimum":"8 GB","recommended":"16 GB"}}],
             "developers":[{"id":10,"name":"Larian Studios","slug":"larian-studios"}],
             "publishers":[],"tags":[],"unknown_field":true}
            """;
    private static final String SCREENSHOTS_JSON = """
            {"count":1,"next":null,"results":[{"id":1,"image":"https://media.rawg.io/shot.jpg"}]}
            """;

    private MockRestServiceServer server;
    private CatalogService service;

    @BeforeEach
    void setUp() {
        service = createService("test-key");
    }

    private CatalogService createService(String apiKey) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        RawgProperties properties = new RawgProperties(BASE, apiKey);
        return new CatalogService(RawgClientConfig.createRawgApi(builder, properties), properties);
    }

    private void expectGame(long id, ExpectedCount count) {
        server.expect(count, requestTo(BASE + "/games/" + id + "?key=test-key"))
                .andRespond(withSuccess(GAME_JSON, MediaType.APPLICATION_JSON));
        server.expect(count, requestTo(BASE + "/games/" + id + "/screenshots?key=test-key"))
                .andRespond(withSuccess(SCREENSHOTS_JSON, MediaType.APPLICATION_JSON));
    }

    @Test
    void getGameMapsDetailsAndAppendsTheKey() {
        expectGame(3328, ExpectedCount.once());

        GameDetails details = service.getGame(3328);

        assertThat(details.name()).isEqualTo("Baldur's Gate 3");
        assertThat(details.requirements().minimum()).isEqualTo("8 GB");
        assertThat(details.screenshots()).containsExactly("https://media.rawg.io/shot.jpg");
        server.verify();
    }

    @Test
    void secondGetGameIsServedFromCache() {
        expectGame(3328, ExpectedCount.once());

        service.getGame(3328);
        service.getGame(3328);

        server.verify();
    }

    @Test
    void unknownGameIsNotFound() {
        server.expect(requestTo(BASE + "/games/999?key=test-key")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> service.getGame(999))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Game 999 not found");
    }

    @Test
    void achievementsAreReadAcrossAllPages() {
        server.expect(requestTo(BASE + "/games/3328/achievements?page=1&page_size=40&key=test-key"))
                .andRespond(withSuccess("""
                        {"count":2,"next":"https://api.rawg.io/api/games/3328/achievements?page=2",
                         "results":[{"id":1,"name":"First","description":"d","image":"i","percent":"50.0"}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/games/3328/achievements?page=2&page_size=40&key=test-key"))
                .andRespond(withSuccess("""
                        {"count":2,"next":null,
                         "results":[{"id":2,"name":"Second","description":"d","image":"i","percent":"5.5"}]}
                        """, MediaType.APPLICATION_JSON));

        List<GameAchievement> achievements = service.getAchievements(3328);

        assertThat(achievements).extracting(GameAchievement::name).containsExactly("First", "Second");
        server.verify();
    }

    @Test
    void searchPagePastTheEndIsEmptyInsteadOfNotFound() {
        server.expect(requestTo(BASE + "/games?page=99&page_size=20&key=test-key"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        PageResponse<GameSummary> page = service.searchGames(
                new GameQuery(null, null, null, null, null, null), new PageParams(99, null));

        assertThat(page.items()).isEmpty();
        assertThat(page.page()).isEqualTo(99);
    }

    @Test
    void missingKeyFailsWithoutCallingRawg() {
        CatalogService keyless = createService("");

        assertThatThrownBy(() -> keyless.getGame(3328))
                .isInstanceOf(RawgUnavailableException.class)
                .hasMessage("RAWG API key not configured");
        server.verify();
    }

    @Test
    void rejectedKeyIsUnavailable() {
        server.expect(requestTo(BASE + "/games/3328?key=test-key")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> service.getGame(3328))
                .isInstanceOf(RawgUnavailableException.class)
                .hasMessage("RAWG API key is invalid");
    }

    @Test
    void rateLimitIsUnavailable() {
        server.expect(requestTo(BASE + "/games/3328?key=test-key")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> service.getGame(3328)).isInstanceOf(RawgUnavailableException.class);
    }

    @Test
    void serverErrorIsUpstreamFailure() {
        server.expect(requestTo(BASE + "/games/3328?key=test-key")).andRespond(withServerError());

        assertThatThrownBy(() -> service.getGame(3328))
                .isInstanceOf(RawgUpstreamException.class)
                .hasMessageNotContaining("test-key");
    }

    @Test
    void timeoutIsUpstreamFailure() {
        server.expect(requestTo(BASE + "/games/3328?key=test-key"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> service.getGame(3328))
                .isInstanceOf(RawgUpstreamException.class)
                .hasMessage("RAWG is not reachable");
    }

    @Test
    void summariesSkipGamesThatFail() {
        expectGame(3328, ExpectedCount.once());
        server.expect(requestTo(BASE + "/games/1?key=test-key")).andRespond(withServerError());

        Map<Long, GameSummary> summaries = service.getSummaries(List.of(3328L, 1L));

        assertThat(summaries).containsOnlyKeys(3328L);
        assertThat(summaries.get(3328L).name()).isEqualTo("Baldur's Gate 3");
    }
}
```

`src/test/java/it/mygamingjournal/catalog/CatalogIntegrationTest.java`
```java
package it.mygamingjournal.catalog;

import it.mygamingjournal.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogIntegrationTest extends IntegrationTest {

    @Test
    void searchIsPublicAndForwardsFilters() throws Exception {
        when(rawgApi.listGames(argThat((Map<String, String> params) ->
                "action".equals(params.get("genres")) && "-rating".equals(params.get("ordering")))))
                .thenReturn(RawgFixtures.page(List.of(RawgFixtures.game(3328, "Baldur's Gate 3")), null));

        mockMvc.perform(get("/api/games").param("genre", "action").param("ordering", "-rating"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(3328))
                .andExpect(jsonPath("$.items[0].name").value("Baldur's Gate 3"))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void invalidOrderingIs400() throws Exception {
        mockMvc.perform(get("/api/games").param("ordering", "drop table"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("ordering"));
    }

    @Test
    void nonNumericGameIdIs400() throws Exception {
        mockMvc.perform(get("/api/games/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void gameDetailsArePublic() throws Exception {
        RawgFixtures.stubGame(rawgApi, 3328, "Baldur's Gate 3");

        mockMvc.perform(get("/api/games/3328"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Baldur's Gate 3"))
                .andExpect(jsonPath("$.community.reviewCount").value(0));
    }

    @Test
    void rawgOutageIs502ProblemDetail() throws Exception {
        when(rawgApi.getGame(anyLong())).thenThrow(new ResourceAccessException("connection refused"));

        mockMvc.perform(get("/api/games/3328"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("RAWG is not reachable"));
    }

    @Test
    void genresAreListed() throws Exception {
        when(rawgApi.getGenres(40)).thenReturn(RawgFixtures.page(
                List.of(new RawgModels.RawgNamed(4, "Action", "action")), null));

        mockMvc.perform(get("/api/genres"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("action"));
    }
}
```

Modify `IntegrationTest` (add the fields and the `@BeforeEach`; imports `org.junit.jupiter.api.BeforeEach`, `org.springframework.test.context.bean.override.mockito.MockitoBean`, `it.mygamingjournal.catalog.RawgApi`, `it.mygamingjournal.catalog.CatalogService`):
```java
    @MockitoBean
    protected RawgApi rawgApi;

    @Autowired
    protected CatalogService catalogService;

    @BeforeEach
    void clearCatalogCaches() {
        // The Spring context (and its caches) is shared between test classes.
        catalogService.clearCaches();
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -Dtest=RawgMapperTest,CatalogServiceTest,CatalogIntegrationTest`
Expected: FAIL (compilation: catalog types not found).

- [ ] **Step 3: Implement RAWG models, client and DTOs**

`catalog/RawgProperties.java`
```java
package it.mygamingjournal.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rawg")
public record RawgProperties(String baseUrl, String apiKey) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
```

`catalog/RawgModels.java`
```java
package it.mygamingjournal.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** RAWG JSON shapes. Internal to the catalog package: never returned by our API. */
public final class RawgModels {

    private RawgModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawgPage<T>(int count, String next, List<T> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawgNamed(long id, String name, String slug) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawgParentPlatform(RawgNamed platform) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawgRequirements(String minimum, String recommended) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawgPlatformEntry(RawgNamed platform, RawgRequirements requirements) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawgGame(
            long id,
            String slug,
            String name,
            String released,
            @JsonProperty("background_image") String backgroundImage,
            Double rating,
            Integer metacritic,
            @JsonProperty("parent_platforms") List<RawgParentPlatform> parentPlatforms,
            List<RawgNamed> genres) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawgGameDetails(
            long id,
            String slug,
            String name,
            @JsonProperty("description_raw") String descriptionRaw,
            String released,
            @JsonProperty("background_image") String backgroundImage,
            Double rating,
            Integer metacritic,
            @JsonProperty("esrb_rating") RawgNamed esrbRating,
            @JsonProperty("reddit_url") String redditUrl,
            @JsonProperty("achievements_count") Integer achievementsCount,
            List<RawgNamed> genres,
            @JsonProperty("parent_platforms") List<RawgParentPlatform> parentPlatforms,
            List<RawgPlatformEntry> platforms,
            List<RawgNamed> developers,
            List<RawgNamed> publishers,
            List<RawgNamed> tags) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawgScreenshot(long id, String image) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RawgAchievement(long id, String name, String description, String image, String percent) {
    }
}
```

`catalog/RawgApi.java`
```java
package it.mygamingjournal.catalog;

import it.mygamingjournal.catalog.RawgModels.RawgAchievement;
import it.mygamingjournal.catalog.RawgModels.RawgGame;
import it.mygamingjournal.catalog.RawgModels.RawgGameDetails;
import it.mygamingjournal.catalog.RawgModels.RawgNamed;
import it.mygamingjournal.catalog.RawgModels.RawgPage;
import it.mygamingjournal.catalog.RawgModels.RawgScreenshot;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.Map;

@HttpExchange
public interface RawgApi {

    @GetExchange("/games")
    RawgPage<RawgGame> listGames(@RequestParam Map<String, String> params);

    @GetExchange("/games/{id}")
    RawgGameDetails getGame(@PathVariable("id") long id);

    @GetExchange("/games/{id}/screenshots")
    RawgPage<RawgScreenshot> getScreenshots(@PathVariable("id") long id);

    @GetExchange("/games/{id}/achievements")
    RawgPage<RawgAchievement> getAchievements(@PathVariable("id") long id,
            @RequestParam("page") int page, @RequestParam("page_size") int pageSize);

    @GetExchange("/games/{id}/game-series")
    RawgPage<RawgGame> getSeries(@PathVariable("id") long id);

    @GetExchange("/games/{id}/additions")
    RawgPage<RawgGame> getAdditions(@PathVariable("id") long id);

    @GetExchange("/games/{id}/parent-games")
    RawgPage<RawgGame> getParentGames(@PathVariable("id") long id);

    @GetExchange("/genres")
    RawgPage<RawgNamed> getGenres(@RequestParam("page_size") int pageSize);

    @GetExchange("/platforms/lists/parents")
    RawgPage<RawgNamed> getParentPlatforms();
}
```

`catalog/RawgClientConfig.java`
```java
package it.mygamingjournal.catalog;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RawgClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    RawgApi rawgApi(RestClient.Builder builder, RawgProperties properties) {
        var requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return createRawgApi(builder.requestFactory(requestFactory), properties);
    }

    /** Public so tests can bind a MockRestServiceServer to the builder first. */
    public static RawgApi createRawgApi(RestClient.Builder builder, RawgProperties properties) {
        RestClient client = builder
                .baseUrl(properties.baseUrl())
                .requestInterceptor(apiKeyInterceptor(properties.apiKey()))
                .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(client)).build().createClient(RawgApi.class);
    }

    private static ClientHttpRequestInterceptor apiKeyInterceptor(String apiKey) {
        return (request, body, execution) -> {
            URI uri = UriComponentsBuilder.fromUri(request.getURI()).queryParam("key", apiKey).build(true).toUri();
            return execution.execute(new HttpRequestWrapper(request) {
                @Override
                public URI getURI() {
                    return uri;
                }
            }, body);
        };
    }
}
```

`catalog/NamedItem.java`
```java
package it.mygamingjournal.catalog;

public record NamedItem(long id, String name, String slug) {
}
```

`catalog/GameSummary.java`
```java
package it.mygamingjournal.catalog;

import java.time.LocalDate;
import java.util.List;

public record GameSummary(long id, String slug, String name, LocalDate released, String coverImage,
                          Double rating, Integer metacritic, List<String> platforms, List<String> genres) {
}
```

`catalog/Requirements.java`
```java
package it.mygamingjournal.catalog;

public record Requirements(String minimum, String recommended) {
}
```

`catalog/CommunityStats.java`
```java
package it.mygamingjournal.catalog;

public record CommunityStats(Double averageScore, long reviewCount, long libraryCount) {

    public static final CommunityStats EMPTY = new CommunityStats(null, 0, 0);
}
```

`catalog/GameDetails.java`
```java
package it.mygamingjournal.catalog;

import java.time.LocalDate;
import java.util.List;

public record GameDetails(
        long id,
        String slug,
        String name,
        String description,
        LocalDate released,
        String coverImage,
        Double rating,
        Integer metacritic,
        String esrbRating,
        List<String> genres,
        List<String> platforms,
        Requirements requirements,
        List<String> developers,
        List<String> publishers,
        List<String> tags,
        List<String> screenshots,
        String redditUrl,
        Integer achievementsCount,
        CommunityStats community) {

    public GameDetails withCommunity(CommunityStats stats) {
        return new GameDetails(id, slug, name, description, released, coverImage, rating, metacritic, esrbRating,
                genres, platforms, requirements, developers, publishers, tags, screenshots, redditUrl,
                achievementsCount, stats);
    }

    public GameSummary toSummary() {
        return new GameSummary(id, slug, name, released, coverImage, rating, metacritic, platforms, genres);
    }
}
```

`catalog/GameAchievement.java`
```java
package it.mygamingjournal.catalog;

public record GameAchievement(long id, String name, String description, String image, double percent) {
}
```

`catalog/RelatedGames.java`
```java
package it.mygamingjournal.catalog;

import java.util.List;

public record RelatedGames(List<GameSummary> series, List<GameSummary> additions, List<GameSummary> parents) {
}
```

`catalog/RawgMapper.java`
```java
package it.mygamingjournal.catalog;

import it.mygamingjournal.catalog.RawgModels.RawgAchievement;
import it.mygamingjournal.catalog.RawgModels.RawgGame;
import it.mygamingjournal.catalog.RawgModels.RawgGameDetails;
import it.mygamingjournal.catalog.RawgModels.RawgNamed;
import it.mygamingjournal.catalog.RawgModels.RawgParentPlatform;
import it.mygamingjournal.catalog.RawgModels.RawgPlatformEntry;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

final class RawgMapper {

    private RawgMapper() {
    }

    static GameSummary toSummary(RawgGame game) {
        return new GameSummary(game.id(), game.slug(), game.name(), parseDate(game.released()),
                blankToNull(game.backgroundImage()), game.rating(), game.metacritic(),
                platformSlugs(game.parentPlatforms()), names(game.genres()));
    }

    static GameDetails toDetails(RawgGameDetails game, List<String> screenshots) {
        return new GameDetails(game.id(), game.slug(), game.name(), blankToNull(game.descriptionRaw()),
                parseDate(game.released()), blankToNull(game.backgroundImage()), game.rating(), game.metacritic(),
                game.esrbRating() == null ? null : game.esrbRating().name(),
                names(game.genres()), platformSlugs(game.parentPlatforms()), pcRequirements(game.platforms()),
                names(game.developers()), names(game.publishers()), names(game.tags()),
                List.copyOf(screenshots), blankToNull(game.redditUrl()), game.achievementsCount(),
                CommunityStats.EMPTY);
    }

    static GameAchievement toAchievement(RawgAchievement achievement) {
        return new GameAchievement(achievement.id(), achievement.name(), achievement.description(),
                blankToNull(achievement.image()), parsePercent(achievement.percent()));
    }

    static NamedItem toNamed(RawgNamed named) {
        return new NamedItem(named.id(), named.name(), named.slug());
    }

    private static Requirements pcRequirements(List<RawgPlatformEntry> platforms) {
        if (platforms == null) {
            return null;
        }
        return platforms.stream()
                .filter(entry -> entry.platform() != null && "pc".equals(entry.platform().slug()))
                .map(RawgPlatformEntry::requirements)
                .filter(Objects::nonNull)
                .filter(req -> req.minimum() != null || req.recommended() != null)
                .map(req -> new Requirements(req.minimum(), req.recommended()))
                .findFirst()
                .orElse(null);
    }

    private static List<String> platformSlugs(List<RawgParentPlatform> platforms) {
        if (platforms == null) {
            return List.of();
        }
        return platforms.stream()
                .map(RawgParentPlatform::platform)
                .filter(Objects::nonNull)
                .map(RawgNamed::slug)
                .toList();
    }

    private static List<String> names(List<RawgNamed> items) {
        return items == null ? List.of() : items.stream().map(RawgNamed::name).toList();
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static double parsePercent(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
```

`catalog/RawgUnavailableException.java`
```java
package it.mygamingjournal.catalog;

/** RAWG cannot be used right now: missing or rejected key, or rate limited. Mapped to 503. */
public class RawgUnavailableException extends RuntimeException {
    public RawgUnavailableException(String message) {
        super(message);
    }
}
```

`catalog/RawgUpstreamException.java`
```java
package it.mygamingjournal.catalog;

/** RAWG failed or did not answer in time. Mapped to 502. */
public class RawgUpstreamException extends RuntimeException {
    public RawgUpstreamException(String message) {
        super(message);
    }
}
```

`catalog/GameQuery.java`
```java
package it.mygamingjournal.catalog;

import it.mygamingjournal.common.paging.PageParams;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.Map;

/** Catalog filters. The v1 views (top, new, coming, genre, developer) are combinations of these. */
public record GameQuery(
        @Size(max = 100) String search,
        @Pattern(regexp = "^[a-z0-9-]+$") String genre,
        @Pattern(regexp = "^[a-z0-9-]+$") String developer,
        @Pattern(regexp = "^\\d+$") String platform,
        @Pattern(regexp = "^-?(name|released|added|created|updated|rating|metacritic)$") String ordering,
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2},\\d{4}-\\d{2}-\\d{2}$") String dates) {

    Map<String, String> toRawgParams(PageParams paging) {
        Map<String, String> params = new LinkedHashMap<>();
        putIfPresent(params, "search", search);
        putIfPresent(params, "genres", genre);
        putIfPresent(params, "developers", developer);
        putIfPresent(params, "parent_platforms", platform);
        putIfPresent(params, "ordering", ordering);
        putIfPresent(params, "dates", dates);
        params.put("page", Integer.toString(paging.page()));
        params.put("page_size", Integer.toString(paging.pageSize()));
        return params;
    }

    private static void putIfPresent(Map<String, String> params, String key, String value) {
        if (value != null && !value.isBlank()) {
            params.put(key, value.trim());
        }
    }
}
```

- [ ] **Step 4: Implement the service, controller and error mapping**

`catalog/CatalogService.java`
```java
package it.mygamingjournal.catalog;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import it.mygamingjournal.catalog.RawgModels.RawgAchievement;
import it.mygamingjournal.catalog.RawgModels.RawgGame;
import it.mygamingjournal.catalog.RawgModels.RawgPage;
import it.mygamingjournal.catalog.RawgModels.RawgScreenshot;
import it.mygamingjournal.common.error.NotFoundException;
import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Single entry point to RAWG. Maps RAWG JSON to our DTOs and keeps short-lived in-memory caches;
 * nothing coming from RAWG is ever persisted.
 */
@Service
public class CatalogService {

    private static final Logger log = LoggerFactory.getLogger(CatalogService.class);
    private static final int ACHIEVEMENT_PAGE_SIZE = 40;
    private static final int MAX_ACHIEVEMENT_PAGES = 20;

    private final RawgApi rawg;
    private final RawgProperties properties;

    private final Cache<String, PageResponse<GameSummary>> searchCache = cache(Duration.ofMinutes(10), 500);
    private final Cache<Long, GameDetails> detailsCache = cache(Duration.ofHours(12), 2000);
    private final Cache<Long, List<GameAchievement>> achievementsCache = cache(Duration.ofHours(12), 500);
    private final Cache<Long, RelatedGames> relatedCache = cache(Duration.ofHours(12), 500);
    private final Cache<String, List<NamedItem>> taxonomyCache = cache(Duration.ofHours(24), 10);

    public CatalogService(RawgApi rawg, RawgProperties properties) {
        this.rawg = rawg;
        this.properties = properties;
    }

    public PageResponse<GameSummary> searchGames(GameQuery query, PageParams paging) {
        Map<String, String> params = query.toRawgParams(paging);
        return searchCache.get(new TreeMap<>(params).toString(), key -> {
            try {
                RawgPage<RawgGame> page = call(() -> rawg.listGames(params));
                List<GameSummary> items = nullSafe(page.results()).stream().map(RawgMapper::toSummary).toList();
                return new PageResponse<>(items, paging.page(), paging.pageSize(), page.count());
            } catch (RawgNotFoundException e) {
                // RAWG answers 404 for a page past the last one.
                return new PageResponse<>(List.of(), paging.page(), paging.pageSize(), 0);
            }
        });
    }

    public GameDetails getGame(long id) {
        return detailsCache.get(id, key -> {
            try {
                var game = call(() -> rawg.getGame(id));
                List<String> screenshots = nullSafe(call(() -> rawg.getScreenshots(id)).results()).stream()
                        .map(RawgScreenshot::image)
                        .toList();
                return RawgMapper.toDetails(game, screenshots);
            } catch (RawgNotFoundException e) {
                throw gameNotFound(id);
            }
        });
    }

    public List<GameAchievement> getAchievements(long id) {
        return achievementsCache.get(id, key -> {
            try {
                List<GameAchievement> all = new ArrayList<>();
                for (int page = 1; page <= MAX_ACHIEVEMENT_PAGES; page++) {
                    int current = page;
                    RawgPage<RawgAchievement> result = call(() -> rawg.getAchievements(id, current, ACHIEVEMENT_PAGE_SIZE));
                    nullSafe(result.results()).forEach(achievement -> all.add(RawgMapper.toAchievement(achievement)));
                    if (result.next() == null) {
                        break;
                    }
                }
                return List.copyOf(all);
            } catch (RawgNotFoundException e) {
                throw gameNotFound(id);
            }
        });
    }

    public RelatedGames getRelated(long id) {
        return relatedCache.get(id, key -> {
            try {
                return new RelatedGames(
                        summaries(call(() -> rawg.getSeries(id))),
                        summaries(call(() -> rawg.getAdditions(id))),
                        summaries(call(() -> rawg.getParentGames(id))));
            } catch (RawgNotFoundException e) {
                throw gameNotFound(id);
            }
        });
    }

    public List<NamedItem> getGenres() {
        return taxonomyCache.get("genres", key ->
                nullSafe(call(() -> rawg.getGenres(40)).results()).stream().map(RawgMapper::toNamed).toList());
    }

    public List<NamedItem> getPlatforms() {
        return taxonomyCache.get("platforms", key ->
                nullSafe(call(rawg::getParentPlatforms).results()).stream().map(RawgMapper::toNamed).toList());
    }

    /**
     * Loads summaries in parallel (virtual threads, through the details cache).
     * Games that cannot be loaded are simply missing from the result.
     */
    public Map<Long, GameSummary> getSummaries(Collection<Long> ids) {
        Map<Long, GameSummary> result = new ConcurrentHashMap<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Long id : Set.copyOf(ids)) {
                executor.submit(() -> {
                    try {
                        result.put(id, getGame(id).toSummary());
                    } catch (RuntimeException e) {
                        log.debug("Game {} could not be loaded from RAWG: {}", id, e.getClass().getSimpleName());
                    }
                });
            }
        }
        return result;
    }

    public void clearCaches() {
        searchCache.invalidateAll();
        detailsCache.invalidateAll();
        achievementsCache.invalidateAll();
        relatedCache.invalidateAll();
        taxonomyCache.invalidateAll();
    }

    private <T> T call(Supplier<T> request) {
        if (!properties.hasApiKey()) {
            throw new RawgUnavailableException("RAWG API key not configured");
        }
        // Never log exception messages here: they contain the request URL, which contains the key.
        try {
            return request.get();
        } catch (HttpClientErrorException.NotFound e) {
            throw new RawgNotFoundException();
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            log.error("RAWG rejected the API key (status {})", e.getStatusCode().value());
            throw new RawgUnavailableException("RAWG API key is invalid");
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("RAWG rate limit reached");
            throw new RawgUnavailableException("RAWG rate limit reached, try again later");
        } catch (RestClientResponseException e) {
            log.warn("RAWG answered with status {}", e.getStatusCode().value());
            throw new RawgUpstreamException("RAWG request failed");
        } catch (ResourceAccessException e) {
            log.warn("RAWG not reachable ({})", e.getCause() == null ? "unknown" : e.getCause().getClass().getSimpleName());
            throw new RawgUpstreamException("RAWG is not reachable");
        }
    }

    private static List<GameSummary> summaries(RawgPage<RawgGame> page) {
        return nullSafe(page.results()).stream().map(RawgMapper::toSummary).toList();
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static NotFoundException gameNotFound(long id) {
        return new NotFoundException("Game " + id + " not found");
    }

    private static <K, V> Cache<K, V> cache(Duration ttl, long maxSize) {
        return Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).build();
    }

    /** RAWG 404, translated by each caller into what it means for that resource. */
    private static final class RawgNotFoundException extends RuntimeException {
    }
}
```

`catalog/CatalogController.java`
```java
package it.mygamingjournal.catalog;

import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalog;

    @GetMapping("/games")
    public PageResponse<GameSummary> searchGames(@Valid GameQuery query, @Valid PageParams paging) {
        return catalog.searchGames(query, paging);
    }

    @GetMapping("/games/{rawgGameId}")
    public GameDetails getGame(@PathVariable @Positive long rawgGameId) {
        return catalog.getGame(rawgGameId);
    }

    @GetMapping("/games/{rawgGameId}/achievements")
    public List<GameAchievement> getAchievements(@PathVariable @Positive long rawgGameId) {
        return catalog.getAchievements(rawgGameId);
    }

    @GetMapping("/games/{rawgGameId}/related")
    public RelatedGames getRelated(@PathVariable @Positive long rawgGameId) {
        return catalog.getRelated(rawgGameId);
    }

    @GetMapping("/genres")
    public List<NamedItem> getGenres() {
        return catalog.getGenres();
    }

    @GetMapping("/platforms")
    public List<NamedItem> getPlatforms() {
        return catalog.getPlatforms();
    }
}
```

Add to `ApiExceptionHandler` (imports `it.mygamingjournal.catalog.RawgUnavailableException`, `it.mygamingjournal.catalog.RawgUpstreamException`):
```java
    @ExceptionHandler(RawgUnavailableException.class)
    ProblemDetail handleRawgUnavailable(RawgUnavailableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(RawgUpstreamException.class)
    ProblemDetail handleRawgUpstream(RawgUpstreamException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: add cached RAWG catalog proxy

<trailer>"
```

---

### Task 5: Library entries, enrichment and progression

**Files:**
- Create: `library/{GameStatus,GameEntry,GameEntryRepository,AchievementProgressId,AchievementProgress,AchievementProgressRepository,PlayAvailability,PlayAvailabilityRepository,UserStats,LibraryStatsService,AvailabilityResponse,LibraryEntryResponse,UpsertEntryRequest,LibrarySort,LibraryService,LibraryController}.java`, `progression/{LevelingSystem,ProgressionService}.java`
- Test: `src/test/java/it/mygamingjournal/progression/LevelingSystemTest.java`, `src/test/java/it/mygamingjournal/library/LibraryIntegrationTest.java`

**Interfaces:**
- Consumes: `CatalogService.getGame`, `CatalogService.getSummaries`, `UserRepository`, `PageParams`, `PageResponse`, `AuthUser`.
- Produces:
  - Entities and repositories for `GameEntry`, `AchievementProgress` (id `AchievementProgressId(Long entryId, Long rawgAchievementId)`), `PlayAvailability` (id = `entryId`).
  - `GameEntryRepository.countByRawgGameId(long): long` (used in Task 10).
  - `LibraryService.requireEntry(long userId, long rawgGameId): GameEntry` (404 "Game is not in your library"), `getLibrary(long userId, GameStatus status, LibrarySort sort, PageParams paging): PageResponse<LibraryEntryResponse>`, `getIndex(long userId): Map<Long, GameStatus>`, `upsert(...)`, `delete(...)`.
  - `LibraryStatsService.statsFor(long userId): UserStats(long totalGames, long completedGames, long wishlistGames, double hoursPlayed, long unlockedAchievements)`.
  - `ProgressionService.recalculate(long userId)`.
  - `AvailabilityResponse(List<String> platforms, List<String> languages, LocalDate availableUntil)` with `static from(PlayAvailability)`.
  - `LibrarySort.fromParam(String)`.

- [ ] **Step 1: Write the failing tests**

`src/test/java/it/mygamingjournal/progression/LevelingSystemTest.java`
```java
package it.mygamingjournal.progression;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LevelingSystemTest {

    private final LevelingSystem leveling = new LevelingSystem();

    @Test
    void experienceWeightsMatchV1() {
        // 3 games * 40 + 1 completed * 120 + 10 achievements * 15 + floor(12.9) hours * 5
        assertThat(leveling.calculateExp(3, 1, 10, 12.9)).isEqualTo(120 + 120 + 150 + 60);
    }

    @Test
    void levelIsOnePerThousandExperience() {
        assertThat(leveling.calculateLevel(999)).isZero();
        assertThat(leveling.calculateLevel(1000)).isEqualTo(1);
        assertThat(leveling.calculateLevel(2500)).isEqualTo(2);
    }
}
```

`src/test/java/it/mygamingjournal/library/LibraryIntegrationTest.java`
```java
package it.mygamingjournal.library;

import it.mygamingjournal.IntegrationTest;
import it.mygamingjournal.catalog.RawgFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LibraryIntegrationTest extends IntegrationTest {

    private void putEntry(TestUser user, long gameId, String json) throws Exception {
        mockMvc.perform(put("/api/me/library/" + gameId).header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    @Test
    void addingAGameReturnsTheEntryWithItsGame() throws Exception {
        TestUser mario = register("mario");
        RawgFixtures.stubGame(rawgApi, 3328, "Baldur's Gate 3");

        mockMvc.perform(put("/api/me/library/3328").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PLAYING\",\"personalRating\":9.5,\"hoursPlayed\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawgGameId").value(3328))
                .andExpect(jsonPath("$.status").value("PLAYING"))
                .andExpect(jsonPath("$.personalRating").value(9.5))
                .andExpect(jsonPath("$.notes").value(""))
                .andExpect(jsonPath("$.unlockedAchievements").value(0))
                .andExpect(jsonPath("$.game.name").value("Baldur's Gate 3"));
    }

    @Test
    void puttingTheSameGameAgainUpdatesInsteadOfDuplicating() throws Exception {
        TestUser mario = register("mario");
        RawgFixtures.stubGame(rawgApi, 3328, "Baldur's Gate 3");

        putEntry(mario, 3328, "{\"status\":\"PLAYING\"}");
        putEntry(mario, 3328, "{\"status\":\"COMPLETED\",\"notes\":\"Great\"}");

        mockMvc.perform(get("/api/me/library").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].notes").value("Great"));
    }

    @Test
    void unknownGameCannotBeAdded() throws Exception {
        TestUser mario = register("mario");
        when(rawgApi.getGame(999)).thenThrow(HttpClientErrorException.create(
                org.springframework.http.HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        mockMvc.perform(put("/api/me/library/999").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PLAYING\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ratingAboveTenIsRejected() throws Exception {
        TestUser mario = register("mario");

        mockMvc.perform(put("/api/me/library/3328").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PLAYING\",\"personalRating\":11}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("personalRating"));
    }

    @Test
    void unknownStatusIsRejected() throws Exception {
        TestUser mario = register("mario");

        mockMvc.perform(put("/api/me/library/3328").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FINISHED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void libraryFiltersByStatusAndSortsByRating() throws Exception {
        TestUser mario = register("mario");
        RawgFixtures.stubGame(rawgApi, 1, "One");
        RawgFixtures.stubGame(rawgApi, 2, "Two");
        RawgFixtures.stubGame(rawgApi, 3, "Three");
        putEntry(mario, 1, "{\"status\":\"COMPLETED\",\"personalRating\":6}");
        putEntry(mario, 2, "{\"status\":\"COMPLETED\",\"personalRating\":9}");
        putEntry(mario, 3, "{\"status\":\"WISHLIST\"}");

        mockMvc.perform(get("/api/me/library").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .param("status", "COMPLETED").param("sort", "rating"))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].rawgGameId").value(2))
                .andExpect(jsonPath("$.items[1].rawgGameId").value(1));
    }

    @Test
    void unsupportedSortIsRejected() throws Exception {
        TestUser mario = register("mario");

        mockMvc.perform(get("/api/me/library").header(HttpHeaders.AUTHORIZATION, bearer(mario)).param("sort", "name"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void entryStillListedWhenRawgFailsForItsGame() throws Exception {
        TestUser mario = register("mario");
        RawgFixtures.stubGame(rawgApi, 3328, "Baldur's Gate 3");
        putEntry(mario, 3328, "{\"status\":\"PLAYING\"}");
        catalogService.clearCaches();
        when(rawgApi.getGame(3328)).thenThrow(new ResourceAccessException("down"));

        mockMvc.perform(get("/api/me/library").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].rawgGameId").value(3328))
                .andExpect(jsonPath("$.items[0].game").value(nullValue()));
    }

    @Test
    void indexMapsGameIdsToStatus() throws Exception {
        TestUser mario = register("mario");
        RawgFixtures.stubGame(rawgApi, 1, "One");
        putEntry(mario, 1, "{\"status\":\"WISHLIST\"}");

        mockMvc.perform(get("/api/me/library/index").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['1']").value("WISHLIST"));
    }

    @Test
    void deletingRemovesTheEntryAndASecondDeleteIs404() throws Exception {
        TestUser mario = register("mario");
        RawgFixtures.stubGame(rawgApi, 1, "One");
        putEntry(mario, 1, "{\"status\":\"WISHLIST\"}");

        mockMvc.perform(delete("/api/me/library/1").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/me/library/1").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isNotFound());
    }

    @Test
    void usersOnlySeeTheirOwnLibrary() throws Exception {
        TestUser mario = register("mario");
        TestUser luigi = register("luigi");
        RawgFixtures.stubGame(rawgApi, 1, "One");
        putEntry(mario, 1, "{\"status\":\"WISHLIST\"}");

        mockMvc.perform(get("/api/me/library").header(HttpHeaders.AUTHORIZATION, bearer(luigi)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void experienceAndLevelFollowTheLibrary() throws Exception {
        TestUser mario = register("mario");
        RawgFixtures.stubGame(rawgApi, 1, "One");

        putEntry(mario, 1, "{\"status\":\"COMPLETED\",\"hoursPlayed\":10.5}");
        assertThat(experienceOf(mario)).isEqualTo(40 + 120 + 10 * 5);

        mockMvc.perform(delete("/api/me/library/1").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isNoContent());
        assertThat(experienceOf(mario)).isZero();
    }

    @Test
    void libraryRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/me/library")).andExpect(status().isUnauthorized());
    }

    private Integer experienceOf(TestUser user) {
        return jdbcTemplate.queryForObject("select experience from users where id = ?", Integer.class, user.id());
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -Dtest=LevelingSystemTest,LibraryIntegrationTest`
Expected: FAIL (compilation: `LevelingSystem` and library types not found).

- [ ] **Step 3: Implement the entities and repositories**

`library/GameStatus.java`
```java
package it.mygamingjournal.library;

public enum GameStatus {
    WISHLIST, PLAYING, COMPLETED, DROPPED, BACKLOG
}
```

`library/GameEntry.java`
```java
package it.mygamingjournal.library;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "game_entries")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "rawg_game_id", nullable = false, updatable = false)
    private long rawgGameId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameStatus status;

    @Column(name = "personal_rating")
    private Double personalRating;

    @Column(name = "hours_played", nullable = false)
    private double hoursPlayed;

    @Column(nullable = false)
    private String notes;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public GameEntry(Long userId, long rawgGameId, Instant now) {
        this.userId = userId;
        this.rawgGameId = rawgGameId;
        this.status = GameStatus.BACKLOG;
        this.notes = "";
        this.addedAt = now;
        this.updatedAt = now;
    }
}
```

`library/GameEntryRepository.java`
```java
package it.mygamingjournal.library;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GameEntryRepository extends JpaRepository<GameEntry, Long> {

    Optional<GameEntry> findByUserIdAndRawgGameId(Long userId, long rawgGameId);

    Page<GameEntry> findByUserId(Long userId, Pageable pageable);

    Page<GameEntry> findByUserIdAndStatus(Long userId, GameStatus status, Pageable pageable);

    long countByRawgGameId(long rawgGameId);

    @Query("select e.rawgGameId as rawgGameId, e.status as status from GameEntry e where e.userId = :userId")
    List<IndexRow> findIndexByUserId(@Param("userId") Long userId);

    @Query("""
            select count(e) as totalGames,
                   coalesce(sum(case when e.status = it.mygamingjournal.library.GameStatus.COMPLETED then 1 else 0 end), 0) as completedGames,
                   coalesce(sum(case when e.status = it.mygamingjournal.library.GameStatus.WISHLIST then 1 else 0 end), 0) as wishlistGames,
                   coalesce(sum(e.hoursPlayed), 0.0) as hoursPlayed
            from GameEntry e
            where e.userId = :userId
            """)
    StatsRow statsForUser(@Param("userId") Long userId);

    interface IndexRow {
        Long getRawgGameId();

        GameStatus getStatus();
    }

    interface StatsRow {
        long getTotalGames();

        long getCompletedGames();

        long getWishlistGames();

        double getHoursPlayed();
    }
}
```

`library/AchievementProgressId.java`
```java
package it.mygamingjournal.library;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AchievementProgressId implements Serializable {
    private Long entryId;
    private Long rawgAchievementId;
}
```

`library/AchievementProgress.java`
```java
package it.mygamingjournal.library;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** A row exists only for unlocked achievements. Name, image etc. come from RAWG. */
@Entity
@Table(name = "achievement_progress")
@IdClass(AchievementProgressId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AchievementProgress {

    @Id
    @Column(name = "entry_id")
    private Long entryId;

    @Id
    @Column(name = "rawg_achievement_id")
    private Long rawgAchievementId;

    @Column(name = "unlocked_at", nullable = false)
    private Instant unlockedAt;
}
```

`library/AchievementProgressRepository.java`
```java
package it.mygamingjournal.library;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AchievementProgressRepository extends JpaRepository<AchievementProgress, AchievementProgressId> {

    List<AchievementProgress> findByEntryId(Long entryId);

    @Query("""
            select p.entryId as entryId, count(p) as unlocked
            from AchievementProgress p
            where p.entryId in :entryIds
            group by p.entryId
            """)
    List<UnlockedCountRow> countUnlockedByEntryIds(@Param("entryIds") Collection<Long> entryIds);

    @Query("""
            select count(p)
            from AchievementProgress p, GameEntry e
            where e.id = p.entryId and e.userId = :userId
            """)
    long countUnlockedByUserId(@Param("userId") Long userId);

    interface UnlockedCountRow {
        Long getEntryId();

        Long getUnlocked();
    }
}
```

`library/PlayAvailability.java`
```java
package it.mygamingjournal.library;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** The row existing means the user is available to play this game (until availableUntil, if set). */
@Entity
@Table(name = "play_availability")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayAvailability {

    @Id
    @Column(name = "entry_id")
    private Long entryId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> platforms = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private List<String> languages = new ArrayList<>();

    @Column(name = "available_until")
    private LocalDate availableUntil;

    public PlayAvailability(Long entryId) {
        this.entryId = entryId;
    }
}
```

`library/PlayAvailabilityRepository.java`
```java
package it.mygamingjournal.library;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayAvailabilityRepository extends JpaRepository<PlayAvailability, Long> {
}
```

- [ ] **Step 4: Implement stats and progression**

`library/UserStats.java`
```java
package it.mygamingjournal.library;

public record UserStats(long totalGames, long completedGames, long wishlistGames, double hoursPlayed,
                        long unlockedAchievements) {
}
```

`library/LibraryStatsService.java`
```java
package it.mygamingjournal.library;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LibraryStatsService {

    private final GameEntryRepository entries;
    private final AchievementProgressRepository progress;

    @Transactional(readOnly = true)
    public UserStats statsFor(long userId) {
        GameEntryRepository.StatsRow row = entries.statsForUser(userId);
        return new UserStats(row.getTotalGames(), row.getCompletedGames(), row.getWishlistGames(),
                row.getHoursPlayed(), progress.countUnlockedByUserId(userId));
    }
}
```

`progression/LevelingSystem.java`
```java
package it.mygamingjournal.progression;

import org.springframework.stereotype.Component;

@Component
public class LevelingSystem {

    private static final int EXP_PER_LEVEL = 1000;
    private static final int WEIGHT_TOTAL_GAMES = 40;
    private static final int WEIGHT_COMPLETED_GAMES = 120;
    private static final int WEIGHT_UNLOCKED_ACHIEVEMENTS = 15;
    private static final int WEIGHT_HOURS_PLAYED = 5;

    public int calculateExp(int totalGames, int completedGames, int unlockedAchievements, double hoursPlayed) {
        int hours = (int) Math.floor(hoursPlayed);
        return totalGames * WEIGHT_TOTAL_GAMES
                + completedGames * WEIGHT_COMPLETED_GAMES
                + unlockedAchievements * WEIGHT_UNLOCKED_ACHIEVEMENTS
                + hours * WEIGHT_HOURS_PLAYED;
    }

    public int calculateLevel(int totalExp) {
        return totalExp / EXP_PER_LEVEL;
    }
}
```

`progression/ProgressionService.java`
```java
package it.mygamingjournal.progression;

import it.mygamingjournal.common.error.NotFoundException;
import it.mygamingjournal.library.LibraryStatsService;
import it.mygamingjournal.library.UserStats;
import it.mygamingjournal.user.User;
import it.mygamingjournal.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProgressionService {

    private final LibraryStatsService stats;
    private final UserRepository users;
    private final LevelingSystem leveling;

    /** Call inside the transaction that changed entries or achievements. */
    @Transactional
    public void recalculate(long userId) {
        UserStats current = stats.statsFor(userId);
        int experience = leveling.calculateExp((int) current.totalGames(), (int) current.completedGames(),
                (int) current.unlockedAchievements(), current.hoursPlayed());
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        user.setExperience(experience);
        user.setLevel(leveling.calculateLevel(experience));
    }
}
```

- [ ] **Step 5: Implement the library service and controller**

`library/AvailabilityResponse.java`
```java
package it.mygamingjournal.library;

import java.time.LocalDate;
import java.util.List;

public record AvailabilityResponse(List<String> platforms, List<String> languages, LocalDate availableUntil) {

    public static AvailabilityResponse from(PlayAvailability availability) {
        return new AvailabilityResponse(List.copyOf(availability.getPlatforms()),
                List.copyOf(availability.getLanguages()), availability.getAvailableUntil());
    }
}
```

`library/LibraryEntryResponse.java`
```java
package it.mygamingjournal.library;

import it.mygamingjournal.catalog.GameSummary;

import java.time.Instant;

public record LibraryEntryResponse(
        long rawgGameId,
        GameStatus status,
        Double personalRating,
        double hoursPlayed,
        String notes,
        Instant addedAt,
        Instant updatedAt,
        long unlockedAchievements,
        AvailabilityResponse availability,
        GameSummary game) {

    static LibraryEntryResponse of(GameEntry entry, long unlockedAchievements, PlayAvailability availability,
                                   GameSummary game) {
        return new LibraryEntryResponse(entry.getRawgGameId(), entry.getStatus(), entry.getPersonalRating(),
                entry.getHoursPlayed(), entry.getNotes(), entry.getAddedAt(), entry.getUpdatedAt(),
                unlockedAchievements, availability == null ? null : AvailabilityResponse.from(availability), game);
    }
}
```

`library/UpsertEntryRequest.java`
```java
package it.mygamingjournal.library;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Full replacement (PUT): omitted optional fields are cleared (rating null, hours 0, notes empty). */
public record UpsertEntryRequest(
        @NotNull GameStatus status,
        @DecimalMin("0.0") @DecimalMax("10.0") Double personalRating,
        @PositiveOrZero @Max(100_000) Double hoursPlayed,
        @Size(max = 5000) String notes) {
}
```

`library/LibrarySort.java`
```java
package it.mygamingjournal.library;

import it.mygamingjournal.common.error.BadRequestException;
import org.springframework.data.domain.Sort;

import java.util.Arrays;

public enum LibrarySort {
    ADDED_AT("addedAt", "addedAt"),
    UPDATED_AT("updatedAt", "updatedAt"),
    RATING("rating", "personalRating"),
    HOURS("hours", "hoursPlayed");

    private final String param;
    private final String property;

    LibrarySort(String param, String property) {
        this.param = param;
        this.property = property;
    }

    public static LibrarySort fromParam(String value) {
        if (value == null || value.isBlank()) {
            return ADDED_AT;
        }
        return Arrays.stream(values())
                .filter(sort -> sort.param.equals(value))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Unsupported sort '" + value + "'. Use addedAt, updatedAt, rating or hours"));
    }

    Sort toSort() {
        return Sort.by(Sort.Order.desc(property).nullsLast(), Sort.Order.desc("id"));
    }
}
```

`library/LibraryService.java`
```java
package it.mygamingjournal.library;

import it.mygamingjournal.catalog.CatalogService;
import it.mygamingjournal.catalog.GameSummary;
import it.mygamingjournal.common.error.NotFoundException;
import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import it.mygamingjournal.progression.ProgressionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LibraryService {

    private final GameEntryRepository entries;
    private final AchievementProgressRepository progress;
    private final PlayAvailabilityRepository availabilities;
    private final CatalogService catalog;
    private final ProgressionService progression;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<LibraryEntryResponse> getLibrary(long userId, GameStatus status, LibrarySort sort,
                                                         PageParams paging) {
        var pageable = paging.toPageable(sort.toSort());
        Page<GameEntry> page = status == null
                ? entries.findByUserId(userId, pageable)
                : entries.findByUserIdAndStatus(userId, status, pageable);
        return new PageResponse<>(toResponses(page.getContent()), paging.page(), paging.pageSize(),
                page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Map<Long, GameStatus> getIndex(long userId) {
        return entries.findIndexByUserId(userId).stream()
                .collect(Collectors.toMap(GameEntryRepository.IndexRow::getRawgGameId,
                        GameEntryRepository.IndexRow::getStatus));
    }

    @Transactional
    public LibraryEntryResponse upsert(long userId, long rawgGameId, UpsertEntryRequest request) {
        Instant now = clock.instant();
        GameEntry entry = entries.findByUserIdAndRawgGameId(userId, rawgGameId).orElseGet(() -> {
            catalog.getGame(rawgGameId); // 404 if RAWG does not know this game
            return new GameEntry(userId, rawgGameId, now);
        });
        entry.setStatus(request.status());
        entry.setPersonalRating(request.personalRating());
        entry.setHoursPlayed(request.hoursPlayed() == null ? 0 : request.hoursPlayed());
        entry.setNotes(request.notes() == null ? "" : request.notes());
        entry.setUpdatedAt(now);
        entries.save(entry);
        progression.recalculate(userId);
        return toResponses(List.of(entry)).getFirst();
    }

    @Transactional
    public void delete(long userId, long rawgGameId) {
        entries.delete(requireEntry(userId, rawgGameId));
        progression.recalculate(userId);
    }

    @Transactional(readOnly = true)
    public GameEntry requireEntry(long userId, long rawgGameId) {
        return entries.findByUserIdAndRawgGameId(userId, rawgGameId)
                .orElseThrow(() -> new NotFoundException("Game is not in your library"));
    }

    private List<LibraryEntryResponse> toResponses(List<GameEntry> page) {
        if (page.isEmpty()) {
            return List.of();
        }
        List<Long> entryIds = page.stream().map(GameEntry::getId).toList();
        Map<Long, Long> unlocked = progress.countUnlockedByEntryIds(entryIds).stream()
                .collect(Collectors.toMap(AchievementProgressRepository.UnlockedCountRow::getEntryId,
                        AchievementProgressRepository.UnlockedCountRow::getUnlocked));
        Map<Long, PlayAvailability> availability = availabilities.findAllById(entryIds).stream()
                .collect(Collectors.toMap(PlayAvailability::getEntryId, Function.identity()));
        Map<Long, GameSummary> games = catalog.getSummaries(page.stream().map(GameEntry::getRawgGameId).toList());
        return page.stream()
                .map(entry -> LibraryEntryResponse.of(entry, unlocked.getOrDefault(entry.getId(), 0L),
                        availability.get(entry.getId()), games.get(entry.getRawgGameId())))
                .toList();
    }
}
```

`library/LibraryController.java`
```java
package it.mygamingjournal.library;

import it.mygamingjournal.auth.AuthUser;
import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/me/library")
@RequiredArgsConstructor
public class LibraryController {

    private final LibraryService library;

    @GetMapping
    public PageResponse<LibraryEntryResponse> getLibrary(@AuthenticationPrincipal AuthUser me,
            @RequestParam(required = false) GameStatus status,
            @RequestParam(required = false) String sort,
            @Valid PageParams paging) {
        return library.getLibrary(me.id(), status, LibrarySort.fromParam(sort), paging);
    }

    @GetMapping("/index")
    public Map<Long, GameStatus> getIndex(@AuthenticationPrincipal AuthUser me) {
        return library.getIndex(me.id());
    }

    @PutMapping("/{rawgGameId}")
    public LibraryEntryResponse upsert(@AuthenticationPrincipal AuthUser me, @PathVariable @Positive long rawgGameId,
            @Valid @RequestBody UpsertEntryRequest request) {
        return library.upsert(me.id(), rawgGameId, request);
    }

    @DeleteMapping("/{rawgGameId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable @Positive long rawgGameId) {
        library.delete(me.id(), rawgGameId);
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./mvnw test`
Expected: PASS. If Hibernate's schema validation rejects a column type (for example `text[]`), fix the entity mapping, not the migration, unless the migration contradicts the spec.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: add library entries with RAWG enrichment and progression

<trailer>"
```

---

### Task 6: Achievement progress

**Files:**
- Create: `library/{AchievementProgressResponse,AchievementService,AchievementController}.java`
- Test: `src/test/java/it/mygamingjournal/library/AchievementIntegrationTest.java`

**Interfaces:**
- Consumes: `LibraryService.requireEntry`, `CatalogService.getAchievements`, `AchievementProgressRepository`, `ProgressionService.recalculate`, `RawgFixtures.stubAchievements`.
- Produces: `GET /api/me/library/{rawgGameId}/achievements`, `PUT`/`DELETE /api/me/library/{rawgGameId}/achievements/{achievementId}` (204, idempotent).

- [ ] **Step 1: Write the failing test**

`src/test/java/it/mygamingjournal/library/AchievementIntegrationTest.java`
```java
package it.mygamingjournal.library;

import it.mygamingjournal.IntegrationTest;
import it.mygamingjournal.catalog.RawgFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AchievementIntegrationTest extends IntegrationTest {

    private TestUser mario;

    @BeforeEach
    void addGameToLibrary() throws Exception {
        mario = register("mario");
        RawgFixtures.stubGame(rawgApi, 3328, "Baldur's Gate 3");
        RawgFixtures.stubAchievements(rawgApi, 3328,
                RawgFixtures.achievement(101, "Descent"), RawgFixtures.achievement(102, "Ascent"));
        mockMvc.perform(put("/api/me/library/3328").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PLAYING\"}"))
                .andExpect(status().isOk());
    }

    private void unlock(long achievementId) throws Exception {
        mockMvc.perform(put("/api/me/library/3328/achievements/" + achievementId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isNoContent());
    }

    @Test
    void listMergesRawgAchievementsWithProgress() throws Exception {
        unlock(102);

        mockMvc.perform(get("/api/me/library/3328/achievements").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[0].unlocked").value(false))
                .andExpect(jsonPath("$[0].unlockedAt").value(nullValue()))
                .andExpect(jsonPath("$[1].name").value("Ascent"))
                .andExpect(jsonPath("$[1].unlocked").value(true))
                .andExpect(jsonPath("$[1].percent").value(12.5));
    }

    @Test
    void unlockIsIdempotentAndCountsOnce() throws Exception {
        unlock(101);
        unlock(101);

        assertThat(jdbcTemplate.queryForObject("select count(*) from achievement_progress", Integer.class)).isEqualTo(1);
        mockMvc.perform(get("/api/me/library").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(jsonPath("$.items[0].unlockedAchievements").value(1));
    }

    @Test
    void lockingIsIdempotent() throws Exception {
        unlock(101);
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(delete("/api/me/library/3328/achievements/101").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                    .andExpect(status().isNoContent());
        }
        assertThat(jdbcTemplate.queryForObject("select count(*) from achievement_progress", Integer.class)).isZero();
    }

    @Test
    void unlockingAddsExperience() throws Exception {
        int before = jdbcTemplate.queryForObject("select experience from users where id = ?", Integer.class, mario.id());
        unlock(101);
        int after = jdbcTemplate.queryForObject("select experience from users where id = ?", Integer.class, mario.id());
        assertThat(after - before).isEqualTo(15);
    }

    @Test
    void achievementOfAnotherGameIs404() throws Exception {
        mockMvc.perform(put("/api/me/library/3328/achievements/999").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Achievement 999 not found for game 3328"));
    }

    @Test
    void gameNotInLibraryIs404() throws Exception {
        TestUser luigi = register("luigi");
        mockMvc.perform(put("/api/me/library/3328/achievements/101").header(HttpHeaders.AUTHORIZATION, bearer(luigi)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Game is not in your library"));
    }

    @Test
    void removingTheGameRemovesItsProgress() throws Exception {
        unlock(101);
        mockMvc.perform(delete("/api/me/library/3328").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("select count(*) from achievement_progress", Integer.class)).isZero();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=AchievementIntegrationTest`
Expected: FAIL (404 on the achievements endpoints: no controller yet).

- [ ] **Step 3: Implement**

`library/AchievementProgressResponse.java`
```java
package it.mygamingjournal.library;

import it.mygamingjournal.catalog.GameAchievement;

import java.time.Instant;

public record AchievementProgressResponse(long id, String name, String description, String image, double percent,
                                          boolean unlocked, Instant unlockedAt) {

    static AchievementProgressResponse of(GameAchievement achievement, Instant unlockedAt) {
        return new AchievementProgressResponse(achievement.id(), achievement.name(), achievement.description(),
                achievement.image(), achievement.percent(), unlockedAt != null, unlockedAt);
    }
}
```

`library/AchievementService.java`
```java
package it.mygamingjournal.library;

import it.mygamingjournal.catalog.CatalogService;
import it.mygamingjournal.common.error.NotFoundException;
import it.mygamingjournal.progression.ProgressionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AchievementService {

    private final LibraryService library;
    private final AchievementProgressRepository progress;
    private final CatalogService catalog;
    private final ProgressionService progression;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<AchievementProgressResponse> list(long userId, long rawgGameId) {
        GameEntry entry = library.requireEntry(userId, rawgGameId);
        Map<Long, Instant> unlocked = progress.findByEntryId(entry.getId()).stream()
                .collect(Collectors.toMap(AchievementProgress::getRawgAchievementId, AchievementProgress::getUnlockedAt));
        return catalog.getAchievements(rawgGameId).stream()
                .map(achievement -> AchievementProgressResponse.of(achievement, unlocked.get(achievement.id())))
                .toList();
    }

    @Transactional
    public void unlock(long userId, long rawgGameId, long achievementId) {
        GameEntry entry = library.requireEntry(userId, rawgGameId);
        boolean belongsToGame = catalog.getAchievements(rawgGameId).stream()
                .anyMatch(achievement -> achievement.id() == achievementId);
        if (!belongsToGame) {
            throw new NotFoundException("Achievement " + achievementId + " not found for game " + rawgGameId);
        }
        AchievementProgressId id = new AchievementProgressId(entry.getId(), achievementId);
        if (!progress.existsById(id)) {
            progress.save(new AchievementProgress(entry.getId(), achievementId, clock.instant()));
            progression.recalculate(userId);
        }
    }

    @Transactional
    public void lock(long userId, long rawgGameId, long achievementId) {
        GameEntry entry = library.requireEntry(userId, rawgGameId);
        AchievementProgressId id = new AchievementProgressId(entry.getId(), achievementId);
        if (progress.existsById(id)) {
            progress.deleteById(id);
            progression.recalculate(userId);
        }
    }
}
```

`library/AchievementController.java`
```java
package it.mygamingjournal.library;

import it.mygamingjournal.auth.AuthUser;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/me/library/{rawgGameId}/achievements")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievements;

    @GetMapping
    public List<AchievementProgressResponse> list(@AuthenticationPrincipal AuthUser me,
            @PathVariable @Positive long rawgGameId) {
        return achievements.list(me.id(), rawgGameId);
    }

    @PutMapping("/{achievementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlock(@AuthenticationPrincipal AuthUser me, @PathVariable @Positive long rawgGameId,
            @PathVariable @Positive long achievementId) {
        achievements.unlock(me.id(), rawgGameId, achievementId);
    }

    @DeleteMapping("/{achievementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void lock(@AuthenticationPrincipal AuthUser me, @PathVariable @Positive long rawgGameId,
            @PathVariable @Positive long achievementId) {
        achievements.lock(me.id(), rawgGameId, achievementId);
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: track achievement progress by RAWG achievement id

<trailer>"
```

---

### Task 7: Follows

**Files:**
- Create: `social/{FollowId,Follow,FollowRepository,FollowService,SocialController}.java`
- Test: `src/test/java/it/mygamingjournal/social/SocialIntegrationTest.java`

**Interfaces:**
- Consumes: `UserRepository`, `UserSummary`, `PageParams`, `PageResponse`, `BadRequestException`, `NotFoundException`.
- Produces: `FollowService.isFollowing(long followerId, long followedId): boolean` (used by Task 8); `GET /api/me/following`, `PUT`/`DELETE /api/me/following/{userId}`.

- [ ] **Step 1: Write the failing test**

`src/test/java/it/mygamingjournal/social/SocialIntegrationTest.java`
```java
package it.mygamingjournal.social;

import it.mygamingjournal.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SocialIntegrationTest extends IntegrationTest {

    @Test
    void followIsIdempotentAndListed() throws Exception {
        TestUser mario = register("mario");
        TestUser luigi = register("luigi");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(put("/api/me/following/" + luigi.id()).header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                    .andExpect(status().isNoContent());
        }

        mockMvc.perform(get("/api/me/following").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].username").value("luigi"))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void followIsOneWay() throws Exception {
        TestUser mario = register("mario");
        TestUser luigi = register("luigi");
        mockMvc.perform(put("/api/me/following/" + luigi.id()).header(HttpHeaders.AUTHORIZATION, bearer(mario)));

        mockMvc.perform(get("/api/me/following").header(HttpHeaders.AUTHORIZATION, bearer(luigi)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void unfollowIsIdempotent() throws Exception {
        TestUser mario = register("mario");
        TestUser luigi = register("luigi");
        mockMvc.perform(put("/api/me/following/" + luigi.id()).header(HttpHeaders.AUTHORIZATION, bearer(mario)));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(delete("/api/me/following/" + luigi.id()).header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(get("/api/me/following").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void followingYourselfIs400() throws Exception {
        TestUser mario = register("mario");
        mockMvc.perform(put("/api/me/following/" + mario.id()).header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("You cannot follow yourself"));
    }

    @Test
    void followingAnUnknownUserIs404() throws Exception {
        TestUser mario = register("mario");
        mockMvc.perform(put("/api/me/following/9999").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=SocialIntegrationTest`
Expected: FAIL (404 from missing endpoints).

- [ ] **Step 3: Implement**

`social/FollowId.java`
```java
package it.mygamingjournal.social;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class FollowId implements Serializable {
    private Long followerId;
    private Long followedId;
}
```

`social/Follow.java`
```java
package it.mygamingjournal.social;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "user_follows")
@IdClass(FollowId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Follow {

    @Id
    @Column(name = "follower_id")
    private Long followerId;

    @Id
    @Column(name = "followed_id")
    private Long followedId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
```

`social/FollowRepository.java`
```java
package it.mygamingjournal.social;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, FollowId> {

    Page<Follow> findByFollowerId(Long followerId, Pageable pageable);
}
```

`social/FollowService.java`
```java
package it.mygamingjournal.social;

import it.mygamingjournal.common.error.BadRequestException;
import it.mygamingjournal.common.error.NotFoundException;
import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import it.mygamingjournal.user.User;
import it.mygamingjournal.user.UserRepository;
import it.mygamingjournal.user.UserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository follows;
    private final UserRepository users;
    private final Clock clock;

    @Transactional
    public void follow(long followerId, long followedId) {
        if (followerId == followedId) {
            throw new BadRequestException("You cannot follow yourself");
        }
        if (!users.existsById(followedId)) {
            throw new NotFoundException("User not found");
        }
        FollowId id = new FollowId(followerId, followedId);
        if (!follows.existsById(id)) {
            follows.save(new Follow(followerId, followedId, clock.instant()));
        }
    }

    @Transactional
    public void unfollow(long followerId, long followedId) {
        follows.deleteById(new FollowId(followerId, followedId));
    }

    @Transactional(readOnly = true)
    public boolean isFollowing(long followerId, long followedId) {
        return follows.existsById(new FollowId(followerId, followedId));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummary> following(long followerId, PageParams paging) {
        Page<Follow> page = follows.findByFollowerId(followerId,
                paging.toPageable(Sort.by(Sort.Order.desc("createdAt"))));
        Map<Long, User> byId = users.findAllById(page.getContent().stream().map(Follow::getFollowedId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        var items = page.getContent().stream()
                .map(follow -> byId.get(follow.getFollowedId()))
                .filter(Objects::nonNull)
                .map(UserSummary::from)
                .toList();
        return new PageResponse<>(items, paging.page(), paging.pageSize(), page.getTotalElements());
    }
}
```

`social/SocialController.java`
```java
package it.mygamingjournal.social;

import it.mygamingjournal.auth.AuthUser;
import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import it.mygamingjournal.user.UserSummary;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/following")
@RequiredArgsConstructor
public class SocialController {

    private final FollowService follows;

    @GetMapping
    public PageResponse<UserSummary> following(@AuthenticationPrincipal AuthUser me, @Valid PageParams paging) {
        return follows.following(me.id(), paging);
    }

    @PutMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void follow(@AuthenticationPrincipal AuthUser me, @PathVariable @Positive long userId) {
        follows.follow(me.id(), userId);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfollow(@AuthenticationPrincipal AuthUser me, @PathVariable @Positive long userId) {
        follows.unfollow(me.id(), userId);
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add one-way follows

<trailer>"
```

---

### Task 8: Own profile and public user profiles

**Files:**
- Create: `user/{GamingAccountsDto,MeResponse,PublicProfileResponse,UpdateProfileRequest,ChangePasswordRequest,ProfileService,UserController}.java`
- Test: `src/test/java/it/mygamingjournal/user/ProfileIntegrationTest.java`

**Interfaces:**
- Consumes: `UserRepository`, `LibraryStatsService.statsFor`, `LibraryService.getLibrary`, `LibrarySort.fromParam`, `FollowService.isFollowing`, `PasswordEncoder`.
- Produces: `ProfileService.requireUser(long): User` (404 "User not found"); endpoints `GET`/`PATCH`/`DELETE /api/me`, `PUT /api/me/password`, `GET /api/users`, `GET /api/users/{userId}`, `GET /api/users/{userId}/library`.

- [ ] **Step 1: Write the failing test**

`src/test/java/it/mygamingjournal/user/ProfileIntegrationTest.java`
```java
package it.mygamingjournal.user;

import it.mygamingjournal.IntegrationTest;
import it.mygamingjournal.catalog.RawgFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileIntegrationTest extends IntegrationTest {

    @Test
    void meReturnsProfileAndStats() throws Exception {
        TestUser mario = register("mario");
        RawgFixtures.stubGame(rawgApi, 1, "One");
        mockMvc.perform(put("/api/me/library/1").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\",\"hoursPlayed\":4}"));

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("mario"))
                .andExpect(jsonPath("$.email").value("mario@example.com"))
                .andExpect(jsonPath("$.stats.totalGames").value(1))
                .andExpect(jsonPath("$.stats.completedGames").value(1))
                .andExpect(jsonPath("$.stats.hoursPlayed").value(4.0))
                .andExpect(jsonPath("$.experience").value(40 + 120 + 20));
    }

    @Test
    void patchUpdatesOnlyTheGivenFields() throws Exception {
        TestUser mario = register("mario");

        mockMvc.perform(patch("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Super Mario","languages":["it","en"],
                                 "gamingAccounts":{"steam":"mario64","discord":" "}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Super Mario"))
                .andExpect(jsonPath("$.languages", contains("en", "it")))
                .andExpect(jsonPath("$.gamingAccounts.steam").value("mario64"))
                .andExpect(jsonPath("$.gamingAccounts.discord").value(nullValue()));

        mockMvc.perform(patch("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bio\":\"Plumber\"}"))
                .andExpect(jsonPath("$.bio").value("Plumber"))
                .andExpect(jsonPath("$.displayName").value("Super Mario"))
                .andExpect(jsonPath("$.gamingAccounts.steam").value("mario64"));
    }

    @Test
    void invalidLanguageCodeIs400() throws Exception {
        TestUser mario = register("mario");
        mockMvc.perform(patch("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"languages\":[\"Italian\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passwordChangeRequiresTheCurrentPassword() throws Exception {
        TestUser mario = register("mario");

        mockMvc.perform(put("/api/me/password").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-one\",\"newPassword\":\"new-password-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Current password is incorrect"));

        mockMvc.perform(put("/api/me/password").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password123\",\"newPassword\":\"new-password-1\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"mario\",\"password\":\"new-password-1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void userSearchMatchesUsernameOrDisplayNameAndIsPublic() throws Exception {
        TestUser mario = register("mario");
        register("luigi");
        register("peach");
        mockMvc.perform(patch("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Red Plumber\"}"));

        mockMvc.perform(get("/api/users").param("search", "PLUMB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].username").value("mario"));
        mockMvc.perform(get("/api/users").param("search", "%"))
                .andExpect(jsonPath("$.items", hasSize(0)));
        mockMvc.perform(get("/api/users"))
                .andExpect(jsonPath("$.totalItems").value(3));
    }

    @Test
    void publicProfileShowsFollowedByMeOnlyWhenAuthenticated() throws Exception {
        TestUser mario = register("mario");
        TestUser luigi = register("luigi");
        mockMvc.perform(put("/api/me/following/" + luigi.id()).header(HttpHeaders.AUTHORIZATION, bearer(mario)));

        mockMvc.perform(get("/api/users/" + luigi.id()).header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("luigi"))
                .andExpect(jsonPath("$.followedByMe").value(true))
                .andExpect(jsonPath("$.email").doesNotExist());
        mockMvc.perform(get("/api/users/" + luigi.id()))
                .andExpect(jsonPath("$.followedByMe").value(nullValue()));
    }

    @Test
    void publicLibraryIsReadableAndUnknownUserIs404() throws Exception {
        TestUser mario = register("mario");
        RawgFixtures.stubGame(rawgApi, 1, "One");
        mockMvc.perform(put("/api/me/library/1").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PLAYING\"}"));

        mockMvc.perform(get("/api/users/" + mario.id() + "/library"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].game.name").value("One"));
        mockMvc.perform(get("/api/users/9999/library")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/users/9999")).andExpect(status().isNotFound());
    }

    @Test
    void deletingTheAccountCascadesEverywhere() throws Exception {
        TestUser mario = register("mario");
        TestUser luigi = register("luigi");
        RawgFixtures.stubGame(rawgApi, 1, "One");
        mockMvc.perform(put("/api/me/library/1").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PLAYING\"}"));
        mockMvc.perform(put("/api/me/following/" + mario.id()).header(HttpHeaders.AUTHORIZATION, bearer(luigi)));

        mockMvc.perform(delete("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/following").header(HttpHeaders.AUTHORIZATION, bearer(luigi)))
                .andExpect(jsonPath("$.totalItems").value(0));
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("select count(*) from game_entries", Integer.class)).isZero();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=ProfileIntegrationTest`
Expected: FAIL (404 from missing endpoints).

- [ ] **Step 3: Implement the DTOs**

`user/GamingAccountsDto.java`
```java
package it.mygamingjournal.user;

import jakarta.validation.constraints.Size;

public record GamingAccountsDto(
        @Size(max = 100) String steam,
        @Size(max = 100) String psn,
        @Size(max = 100) String xbox,
        @Size(max = 100) String nintendo,
        @Size(max = 100) String epic,
        @Size(max = 100) String riot,
        @Size(max = 100) String discord) {

    static GamingAccountsDto from(GamingAccounts accounts) {
        return new GamingAccountsDto(accounts.getSteam(), accounts.getPsn(), accounts.getXbox(),
                accounts.getNintendo(), accounts.getEpic(), accounts.getRiot(), accounts.getDiscord());
    }

    GamingAccounts toEmbeddable() {
        return new GamingAccounts(clean(steam), clean(psn), clean(xbox), clean(nintendo), clean(epic),
                clean(riot), clean(discord));
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
```

`user/MeResponse.java`
```java
package it.mygamingjournal.user;

import it.mygamingjournal.library.UserStats;

import java.time.Instant;
import java.util.List;

public record MeResponse(long id, String username, String email, String displayName, String avatarUrl, String bio,
                         List<String> languages, GamingAccountsDto gamingAccounts, int experience, int level,
                         Instant createdAt, UserStats stats) {

    static MeResponse from(User user, UserStats stats) {
        return new MeResponse(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName(),
                user.getAvatarUrl(), user.getBio(), user.getLanguages().stream().sorted().toList(),
                GamingAccountsDto.from(user.getGamingAccounts()), user.getExperience(), user.getLevel(),
                user.getCreatedAt(), stats);
    }
}
```

`user/PublicProfileResponse.java`
```java
package it.mygamingjournal.user;

import it.mygamingjournal.library.UserStats;

import java.time.Instant;
import java.util.List;

/** No email: this is visible to everyone. followedByMe is null for anonymous viewers. */
public record PublicProfileResponse(long id, String username, String displayName, String avatarUrl, String bio,
                                    List<String> languages, GamingAccountsDto gamingAccounts, int experience,
                                    int level, Instant createdAt, UserStats stats, Boolean followedByMe) {

    static PublicProfileResponse from(User user, UserStats stats, Boolean followedByMe) {
        return new PublicProfileResponse(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getAvatarUrl(), user.getBio(), user.getLanguages().stream().sorted().toList(),
                GamingAccountsDto.from(user.getGamingAccounts()), user.getExperience(), user.getLevel(),
                user.getCreatedAt(), stats, followedByMe);
    }
}
```

`user/UpdateProfileRequest.java`
```java
package it.mygamingjournal.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Partial update: null fields are left unchanged. An empty bio clears it. */
public record UpdateProfileRequest(
        @Size(min = 1, max = 50) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String displayName,
        @Size(max = 1000) String bio,
        @Size(max = 10) List<@Pattern(regexp = "^[a-z]{2}$", message = "must be an ISO 639-1 code") String> languages,
        @Valid GamingAccountsDto gamingAccounts) {
}
```

`user/ChangePasswordRequest.java`
```java
package it.mygamingjournal.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(@NotBlank String currentPassword,
                                    @NotBlank @Size(min = 8, max = 72) String newPassword) {
}
```

- [ ] **Step 4: Implement the service and controller**

`user/ProfileService.java`
```java
package it.mygamingjournal.user;

import it.mygamingjournal.common.error.BadRequestException;
import it.mygamingjournal.common.error.NotFoundException;
import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import it.mygamingjournal.library.LibraryStatsService;
import it.mygamingjournal.social.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository users;
    private final LibraryStatsService stats;
    private final FollowService follows;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public User requireUser(long userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public MeResponse getMe(long userId) {
        return MeResponse.from(requireUser(userId), stats.statsFor(userId));
    }

    @Transactional
    public MeResponse update(long userId, UpdateProfileRequest request) {
        User user = requireUser(userId);
        if (request.displayName() != null) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.bio() != null) {
            user.setBio(request.bio().isBlank() ? null : request.bio().trim());
        }
        if (request.languages() != null) {
            user.getLanguages().clear();
            user.getLanguages().addAll(request.languages());
        }
        if (request.gamingAccounts() != null) {
            user.setGamingAccounts(request.gamingAccounts().toEmbeddable());
        }
        return MeResponse.from(user, stats.statsFor(userId));
    }

    @Transactional
    public void changePassword(long userId, ChangePasswordRequest request) {
        User user = requireUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    /** Database cascades remove entries, progress, availability, reviews and follows. */
    @Transactional
    public void delete(long userId) {
        users.delete(requireUser(userId));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummary> search(String search, PageParams paging) {
        var pageable = paging.toPageable(Sort.by("username"));
        Page<User> page = search == null || search.isBlank()
                ? users.findAll(pageable)
                : users.findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
                        search.trim(), search.trim(), pageable);
        return PageResponse.of(page, paging, UserSummary::from);
    }

    @Transactional(readOnly = true)
    public PublicProfileResponse getPublicProfile(long userId, Long viewerId) {
        User user = requireUser(userId);
        Boolean followedByMe = viewerId == null ? null : follows.isFollowing(viewerId, userId);
        return PublicProfileResponse.from(user, stats.statsFor(userId), followedByMe);
    }
}
```

`user/UserController.java`
```java
package it.mygamingjournal.user;

import it.mygamingjournal.auth.AuthUser;
import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import it.mygamingjournal.library.GameStatus;
import it.mygamingjournal.library.LibraryEntryResponse;
import it.mygamingjournal.library.LibraryService;
import it.mygamingjournal.library.LibrarySort;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final ProfileService profiles;
    private final LibraryService library;

    @GetMapping("/me")
    public MeResponse getMe(@AuthenticationPrincipal AuthUser me) {
        return profiles.getMe(me.id());
    }

    @PatchMapping("/me")
    public MeResponse updateMe(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UpdateProfileRequest request) {
        return profiles.update(me.id(), request);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ChangePasswordRequest request) {
        profiles.changePassword(me.id(), request);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@AuthenticationPrincipal AuthUser me) {
        profiles.delete(me.id());
    }

    @GetMapping("/users")
    public PageResponse<UserSummary> searchUsers(@RequestParam(required = false) @Size(max = 50) String search,
            @Valid PageParams paging) {
        return profiles.search(search, paging);
    }

    @GetMapping("/users/{userId}")
    public PublicProfileResponse getUser(@AuthenticationPrincipal AuthUser me, @PathVariable @Positive long userId) {
        return profiles.getPublicProfile(userId, me == null ? null : me.id());
    }

    @GetMapping("/users/{userId}/library")
    public PageResponse<LibraryEntryResponse> getUserLibrary(@PathVariable @Positive long userId,
            @RequestParam(required = false) GameStatus status,
            @RequestParam(required = false) String sort,
            @Valid PageParams paging) {
        profiles.requireUser(userId);
        return library.getLibrary(userId, status, LibrarySort.fromParam(sort), paging);
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw test`
Expected: PASS. Note `/api/me` for a deleted user returns 404 because the token is still valid but the user is gone.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: add own profile management and public user profiles

<trailer>"
```

---

### Task 9: Avatar upload

**Files:**
- Create: `media/{ImageStorage,CloudinaryImageStorage,ImageSignatures,MediaUnavailableException}.java`, `user/{AvatarResponse,AvatarService,AvatarController}.java`
- Modify: `common/error/ApiExceptionHandler.java` (503 for media), `src/test/java/it/mygamingjournal/IntegrationTest.java` (mock `ImageStorage`)
- Test: `src/test/java/it/mygamingjournal/media/{ImageSignaturesTest,CloudinaryImageStorageTest}.java`, `src/test/java/it/mygamingjournal/user/AvatarIntegrationTest.java`

**Interfaces:**
- Consumes: `ProfileService.requireUser`.
- Produces: `ImageStorage.uploadAvatar(long userId, byte[] content): String` (public URL); `ImageSignatures.isSupported(byte[]): boolean`; `PUT /api/me/avatar` (multipart part `file`) → `{ avatarUrl }`; `IntegrationTest.imageStorage` mock.

- [ ] **Step 1: Write the failing tests**

`src/test/java/it/mygamingjournal/media/ImageSignaturesTest.java`
```java
package it.mygamingjournal.media;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ImageSignaturesTest {

    @Test
    void recognisesJpegPngAndWebp() {
        assertThat(ImageSignatures.isSupported(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00})).isTrue();
        assertThat(ImageSignatures.isSupported(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})).isTrue();
        assertThat(ImageSignatures.isSupported("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1))).isTrue();
    }

    @Test
    void rejectsEverythingElse() {
        assertThat(ImageSignatures.isSupported("hello world".getBytes(StandardCharsets.UTF_8))).isFalse();
        assertThat(ImageSignatures.isSupported("GIF89a".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(ImageSignatures.isSupported(new byte[0])).isFalse();
    }
}
```

`src/test/java/it/mygamingjournal/media/CloudinaryImageStorageTest.java`
```java
package it.mygamingjournal.media;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudinaryImageStorageTest {

    @Test
    void missingConfigurationIsUnavailable() {
        CloudinaryImageStorage storage = new CloudinaryImageStorage("");

        assertThatThrownBy(() -> storage.uploadAvatar(1L, new byte[]{1}))
                .isInstanceOf(MediaUnavailableException.class)
                .hasMessage("Image upload is not configured");
    }
}
```

Modify `IntegrationTest` (import `it.mygamingjournal.media.ImageStorage`):
```java
    @MockitoBean
    protected ImageStorage imageStorage;
```

`src/test/java/it/mygamingjournal/user/AvatarIntegrationTest.java`
```java
package it.mygamingjournal.user;

import it.mygamingjournal.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AvatarIntegrationTest extends IntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Test
    void validImageIsUploadedAndSaved() throws Exception {
        TestUser mario = register("mario");
        when(imageStorage.uploadAvatar(eq(mario.id()), any())).thenReturn("https://cdn.test/mario.png");

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/me/avatar")
                        .file(new MockMultipartFile("file", "me.png", "image/png", PNG))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value("https://cdn.test/mario.png"));

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(jsonPath("$.avatarUrl").value("https://cdn.test/mario.png"));
    }

    @Test
    void textFilePretendingToBePngIsRejected() throws Exception {
        TestUser mario = register("mario");

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/me/avatar")
                        .file(new MockMultipartFile("file", "evil.png", "image/png",
                                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Avatar must be a JPEG, PNG or WebP image"));
        verify(imageStorage, never()).uploadAvatar(anyLong(), any());
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        TestUser mario = register("mario");

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/me/avatar")
                        .file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./mvnw test -Dtest=ImageSignaturesTest,CloudinaryImageStorageTest,AvatarIntegrationTest`
Expected: FAIL (compilation: media types not found).

- [ ] **Step 3: Implement**

`media/MediaUnavailableException.java`
```java
package it.mygamingjournal.media;

public class MediaUnavailableException extends RuntimeException {
    public MediaUnavailableException(String message) {
        super(message);
    }
}
```

`media/ImageStorage.java`
```java
package it.mygamingjournal.media;

public interface ImageStorage {

    /** Stores the avatar (replacing any previous one) and returns its public HTTPS URL. */
    String uploadAvatar(long userId, byte[] content);
}
```

`media/CloudinaryImageStorage.java`
```java
package it.mygamingjournal.media;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class CloudinaryImageStorage implements ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryImageStorage.class);

    private final Cloudinary cloudinary;

    public CloudinaryImageStorage(@Value("${app.cloudinary.url:}") String cloudinaryUrl) {
        this.cloudinary = cloudinaryUrl == null || cloudinaryUrl.isBlank() ? null : new Cloudinary(cloudinaryUrl);
    }

    @Override
    public String uploadAvatar(long userId, byte[] content) {
        if (cloudinary == null) {
            throw new MediaUnavailableException("Image upload is not configured");
        }
        try {
            Map<?, ?> result = cloudinary.uploader().upload(content, ObjectUtils.asMap(
                    "folder", "mygamingjournal/avatars",
                    "public_id", "user-" + userId,
                    "overwrite", true,
                    "resource_type", "image"));
            return (String) result.get("secure_url");
        } catch (IOException | RuntimeException e) {
            log.warn("Avatar upload failed for user {}: {}", userId, e.getClass().getSimpleName());
            throw new MediaUnavailableException("Image upload failed");
        }
    }
}
```

`media/ImageSignatures.java`
```java
package it.mygamingjournal.media;

/** Detects images by their magic bytes; the client-declared content type is not trusted. */
public final class ImageSignatures {

    private ImageSignatures() {
    }

    public static boolean isSupported(byte[] content) {
        return isJpeg(content) || isPng(content) || isWebp(content);
    }

    private static boolean isJpeg(byte[] c) {
        return c.length >= 3 && (c[0] & 0xFF) == 0xFF && (c[1] & 0xFF) == 0xD8 && (c[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] c) {
        return c.length >= 8 && (c[0] & 0xFF) == 0x89 && c[1] == 'P' && c[2] == 'N' && c[3] == 'G'
                && c[4] == 0x0D && c[5] == 0x0A && c[6] == 0x1A && c[7] == 0x0A;
    }

    private static boolean isWebp(byte[] c) {
        return c.length >= 12 && c[0] == 'R' && c[1] == 'I' && c[2] == 'F' && c[3] == 'F'
                && c[8] == 'W' && c[9] == 'E' && c[10] == 'B' && c[11] == 'P';
    }
}
```

`user/AvatarResponse.java`
```java
package it.mygamingjournal.user;

public record AvatarResponse(String avatarUrl) {
}
```

`user/AvatarService.java`
```java
package it.mygamingjournal.user;

import it.mygamingjournal.common.error.BadRequestException;
import it.mygamingjournal.media.ImageSignatures;
import it.mygamingjournal.media.ImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class AvatarService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final ProfileService profiles;
    private final ImageStorage storage;

    @Transactional
    public AvatarResponse updateAvatar(long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Avatar file is required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("Avatar must be at most 5 MB");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Avatar file could not be read");
        }
        if (!ImageSignatures.isSupported(content)) {
            throw new BadRequestException("Avatar must be a JPEG, PNG or WebP image");
        }
        User user = profiles.requireUser(userId);
        String url = storage.uploadAvatar(userId, content);
        user.setAvatarUrl(url);
        return new AvatarResponse(url);
    }
}
```

`user/AvatarController.java`
```java
package it.mygamingjournal.user;

import it.mygamingjournal.auth.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class AvatarController {

    private final AvatarService avatars;

    @PutMapping(path = "/api/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AvatarResponse updateAvatar(@AuthenticationPrincipal AuthUser me, @RequestParam("file") MultipartFile file) {
        return avatars.updateAvatar(me.id(), file);
    }
}
```

Add to `ApiExceptionHandler` (import `it.mygamingjournal.media.MediaUnavailableException`):
```java
    @ExceptionHandler(MediaUnavailableException.class)
    ProblemDetail handleMediaUnavailable(MediaUnavailableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add avatar upload with magic-byte validation

<trailer>"
```

---

### Task 10: Reviews and community stats

**Files:**
- Create: `review/{Review,ReviewRepository,ReviewRequest,ReviewResponse,ReviewService,ReviewController,CommunityStatsService}.java`
- Modify: `catalog/CatalogController.java` (`getGame` adds community stats)
- Test: `src/test/java/it/mygamingjournal/review/ReviewIntegrationTest.java`

**Interfaces:**
- Consumes: `CatalogService.getGame`, `CatalogService.getSummaries`, `UserRepository`, `UserSummary`, `ProfileService.requireUser`, `GameEntryRepository.countByRawgGameId`, `GameDetails.withCommunity`.
- Produces: `GET /api/games/{rawgGameId}/reviews`, `PUT`/`DELETE /api/games/{rawgGameId}/reviews/me`, `GET /api/users/{userId}/reviews`; `CommunityStatsService.forGame(long): CommunityStats`.

- [ ] **Step 1: Write the failing test**

`src/test/java/it/mygamingjournal/review/ReviewIntegrationTest.java`
```java
package it.mygamingjournal.review;

import it.mygamingjournal.IntegrationTest;
import it.mygamingjournal.catalog.RawgFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewIntegrationTest extends IntegrationTest {

    @BeforeEach
    void stubGame() {
        RawgFixtures.stubGame(rawgApi, 3328, "Baldur's Gate 3");
    }

    private void review(TestUser user, String text, int score) throws Exception {
        mockMvc.perform(put("/api/games/3328/reviews/me").header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"%s\",\"score\":%d}".formatted(text, score)))
                .andExpect(status().isOk());
    }

    @Test
    void oneReviewPerUserPerGame() throws Exception {
        TestUser mario = register("mario");
        review(mario, "Good", 7);
        review(mario, "Actually great", 10);

        mockMvc.perform(get("/api/games/3328/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].text").value("Actually great"))
                .andExpect(jsonPath("$.items[0].score").value(10))
                .andExpect(jsonPath("$.items[0].author.username").value("mario"))
                .andExpect(jsonPath("$.items[0].game").value(nullValue()));
    }

    @Test
    void scoreOutOfRangeIs400() throws Exception {
        TestUser mario = register("mario");
        mockMvc.perform(put("/api/games/3328/reviews/me").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Bad\",\"score\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("score"));
    }

    @Test
    void writingRequiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/games/3328/reviews/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Good\",\"score\":7}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteIsIdempotent() throws Exception {
        TestUser mario = register("mario");
        review(mario, "Good", 7);
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(delete("/api/games/3328/reviews/me").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                    .andExpect(status().isNoContent());
        }
        mockMvc.perform(get("/api/games/3328/reviews")).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void userReviewsIncludeTheGame() throws Exception {
        TestUser mario = register("mario");
        review(mario, "Good", 7);

        mockMvc.perform(get("/api/users/" + mario.id() + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].game.name").value("Baldur's Gate 3"));
        mockMvc.perform(get("/api/users/9999/reviews")).andExpect(status().isNotFound());
    }

    @Test
    void gameDetailsCarryCommunityStats() throws Exception {
        TestUser mario = register("mario");
        TestUser luigi = register("luigi");
        review(mario, "Good", 7);
        review(luigi, "Great", 10);
        mockMvc.perform(put("/api/me/library/3328").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PLAYING\"}"));

        mockMvc.perform(get("/api/games/3328"))
                .andExpect(jsonPath("$.community.averageScore").value(8.5))
                .andExpect(jsonPath("$.community.reviewCount").value(2))
                .andExpect(jsonPath("$.community.libraryCount").value(1));
    }

    @Test
    void deletedAccountsReviewsDisappear() throws Exception {
        TestUser mario = register("mario");
        review(mario, "Good", 7);
        mockMvc.perform(delete("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/games/3328/reviews")).andExpect(jsonPath("$.totalItems").value(0));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=ReviewIntegrationTest`
Expected: FAIL (missing endpoints; community stats are zero).

- [ ] **Step 3: Implement**

`review/Review.java`
```java
package it.mygamingjournal.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "rawg_game_id", nullable = false, updatable = false)
    private long rawgGameId;

    @Column(nullable = false)
    private String text;

    @Column(nullable = false)
    private int score;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Review(Long userId, long rawgGameId, Instant now) {
        this.userId = userId;
        this.rawgGameId = rawgGameId;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
```

`review/ReviewRepository.java`
```java
package it.mygamingjournal.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByUserIdAndRawgGameId(Long userId, long rawgGameId);

    Page<Review> findByRawgGameId(long rawgGameId, Pageable pageable);

    Page<Review> findByUserId(Long userId, Pageable pageable);

    long deleteByUserIdAndRawgGameId(Long userId, long rawgGameId);

    @Query("select avg(r.score) as averageScore, count(r) as reviewCount from Review r where r.rawgGameId = :rawgGameId")
    StatsRow statsForGame(@Param("rawgGameId") long rawgGameId);

    interface StatsRow {
        Double getAverageScore();

        long getReviewCount();
    }
}
```

`review/ReviewRequest.java`
```java
package it.mygamingjournal.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewRequest(@NotBlank @Size(max = 5000) String text, @NotNull @Min(1) @Max(10) Integer score) {
}
```

`review/ReviewResponse.java`
```java
package it.mygamingjournal.review;

import it.mygamingjournal.catalog.GameSummary;
import it.mygamingjournal.user.UserSummary;

import java.time.Instant;

/** game is filled only when listing a user's reviews; author is null if the user no longer exists. */
public record ReviewResponse(long id, long rawgGameId, String text, int score, Instant createdAt, Instant updatedAt,
                             UserSummary author, GameSummary game) {

    static ReviewResponse of(Review review, UserSummary author, GameSummary game) {
        return new ReviewResponse(review.getId(), review.getRawgGameId(), review.getText(), review.getScore(),
                review.getCreatedAt(), review.getUpdatedAt(), author, game);
    }
}
```

`review/ReviewService.java`
```java
package it.mygamingjournal.review;

import it.mygamingjournal.catalog.CatalogService;
import it.mygamingjournal.catalog.GameSummary;
import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import it.mygamingjournal.user.ProfileService;
import it.mygamingjournal.user.UserRepository;
import it.mygamingjournal.user.UserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ReviewRepository reviews;
    private final UserRepository users;
    private final ProfileService profiles;
    private final CatalogService catalog;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> listForGame(long rawgGameId, PageParams paging) {
        Page<Review> page = reviews.findByRawgGameId(rawgGameId, paging.toPageable(NEWEST_FIRST));
        Map<Long, UserSummary> authors = authorsOf(page);
        return PageResponse.of(page, paging, review -> ReviewResponse.of(review, authors.get(review.getUserId()), null));
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> listForUser(long userId, PageParams paging) {
        UserSummary author = UserSummary.from(profiles.requireUser(userId));
        Page<Review> page = reviews.findByUserId(userId, paging.toPageable(NEWEST_FIRST));
        Map<Long, GameSummary> games = catalog.getSummaries(page.getContent().stream().map(Review::getRawgGameId).toList());
        return PageResponse.of(page, paging, review -> ReviewResponse.of(review, author, games.get(review.getRawgGameId())));
    }

    @Transactional
    public ReviewResponse upsertMine(long userId, long rawgGameId, ReviewRequest request) {
        Instant now = clock.instant();
        Review review = reviews.findByUserIdAndRawgGameId(userId, rawgGameId).orElseGet(() -> {
            catalog.getGame(rawgGameId); // 404 if RAWG does not know this game
            return new Review(userId, rawgGameId, now);
        });
        review.setText(request.text().trim());
        review.setScore(request.score());
        review.setUpdatedAt(now);
        reviews.save(review);
        return ReviewResponse.of(review, UserSummary.from(profiles.requireUser(userId)), null);
    }

    @Transactional
    public void deleteMine(long userId, long rawgGameId) {
        reviews.deleteByUserIdAndRawgGameId(userId, rawgGameId);
    }

    private Map<Long, UserSummary> authorsOf(Page<Review> page) {
        return users.findAllById(page.getContent().stream().map(Review::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(user -> user.getId(), UserSummary::from));
    }
}
```

`review/CommunityStatsService.java`
```java
package it.mygamingjournal.review;

import it.mygamingjournal.catalog.CommunityStats;
import it.mygamingjournal.library.GameEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommunityStatsService {

    private final ReviewRepository reviews;
    private final GameEntryRepository entries;

    @Transactional(readOnly = true)
    public CommunityStats forGame(long rawgGameId) {
        ReviewRepository.StatsRow row = reviews.statsForGame(rawgGameId);
        Double average = row.getAverageScore() == null ? null : Math.round(row.getAverageScore() * 10) / 10.0;
        return new CommunityStats(average, row.getReviewCount(), entries.countByRawgGameId(rawgGameId));
    }
}
```

`review/ReviewController.java`
```java
package it.mygamingjournal.review;

import it.mygamingjournal.auth.AuthUser;
import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviews;

    @GetMapping("/games/{rawgGameId}/reviews")
    public PageResponse<ReviewResponse> listForGame(@PathVariable @Positive long rawgGameId, @Valid PageParams paging) {
        return reviews.listForGame(rawgGameId, paging);
    }

    @PutMapping("/games/{rawgGameId}/reviews/me")
    public ReviewResponse upsertMine(@AuthenticationPrincipal AuthUser me, @PathVariable @Positive long rawgGameId,
            @Valid @RequestBody ReviewRequest request) {
        return reviews.upsertMine(me.id(), rawgGameId, request);
    }

    @DeleteMapping("/games/{rawgGameId}/reviews/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMine(@AuthenticationPrincipal AuthUser me, @PathVariable @Positive long rawgGameId) {
        reviews.deleteMine(me.id(), rawgGameId);
    }

    @GetMapping("/users/{userId}/reviews")
    public PageResponse<ReviewResponse> listForUser(@PathVariable @Positive long userId, @Valid PageParams paging) {
        return reviews.listForUser(userId, paging);
    }
}
```

Modify `catalog/CatalogController.java`: inject `CommunityStatsService` and replace `getGame`:
```java
    private final CommunityStatsService communityStats;

    @GetMapping("/games/{rawgGameId}")
    public GameDetails getGame(@PathVariable @Positive long rawgGameId) {
        return catalog.getGame(rawgGameId).withCommunity(communityStats.forGame(rawgGameId));
    }
```
(import `it.mygamingjournal.review.CommunityStatsService`; the field goes next to `catalog`, and `@RequiredArgsConstructor` picks it up.)

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./mvnw test`
Expected: PASS (including `CatalogIntegrationTest.gameDetailsArePublic`, which still sees `reviewCount` 0).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add reviews and community stats on game details

<trailer>"
```

---

### Task 11: Play availability and matchmaking

**Files:**
- Create: `library/{AvailabilityRequest,AvailabilityService}.java`, `matchmaking/{AvailablePlayerResponse,MatchmakingService,MatchmakingController}.java`
- Modify: `library/LibraryController.java` (availability endpoints)
- Test: `src/test/java/it/mygamingjournal/matchmaking/MatchmakingIntegrationTest.java`

**Interfaces:**
- Consumes: `LibraryService.requireEntry`, `PlayAvailabilityRepository`, `AvailabilityResponse`, `UserSummary`, `NamedParameterJdbcTemplate`, `Clock`.
- Produces: `PUT`/`DELETE /api/me/library/{rawgGameId}/availability`; `GET /api/games/{rawgGameId}/available-players?platform&language&page&pageSize` (authenticated).

- [ ] **Step 1: Write the failing test**

`src/test/java/it/mygamingjournal/matchmaking/MatchmakingIntegrationTest.java`
```java
package it.mygamingjournal.matchmaking;

import it.mygamingjournal.IntegrationTest;
import it.mygamingjournal.catalog.RawgFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MatchmakingIntegrationTest extends IntegrationTest {

    private TestUser mario;
    private TestUser luigi;
    private TestUser peach;

    @BeforeEach
    void setUpPlayers() throws Exception {
        RawgFixtures.stubGame(rawgApi, 3328, "Baldur's Gate 3");
        mario = register("mario");
        luigi = register("luigi");
        peach = register("peach");
        for (TestUser user : new TestUser[]{mario, luigi, peach}) {
            mockMvc.perform(put("/api/me/library/3328").header(HttpHeaders.AUTHORIZATION, bearer(user))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PLAYING\"}"))
                    .andExpect(status().isOk());
        }
    }

    private void setAvailability(TestUser user, String json) throws Exception {
        mockMvc.perform(put("/api/me/library/3328/availability").header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    @Test
    void availablePlayersExcludeMeAndFilterByPlatformAndLanguage() throws Exception {
        setAvailability(mario, "{\"platforms\":[\"pc\"],\"languages\":[\"it\"]}");
        setAvailability(luigi, "{\"platforms\":[\"pc\",\"playstation\"],\"languages\":[\"en\"]}");
        setAvailability(peach, "{\"platforms\":[\"xbox\"],\"languages\":[\"it\"]}");

        mockMvc.perform(get("/api/games/3328/available-players").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].user.username").value("luigi"))
                .andExpect(jsonPath("$.items[1].user.username").value("peach"));

        mockMvc.perform(get("/api/games/3328/available-players").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .param("platform", "pc"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].user.username").value("luigi"))
                .andExpect(jsonPath("$.items[0].platforms[1]").value("playstation"));

        mockMvc.perform(get("/api/games/3328/available-players").header(HttpHeaders.AUTHORIZATION, bearer(mario))
                        .param("language", "it"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].user.username").value("peach"));
    }

    @Test
    void expiredAvailabilityIsHidden() throws Exception {
        setAvailability(luigi, "{\"platforms\":[\"pc\"],\"languages\":[\"en\"]}");
        // Two days back, so the local-vs-UTC date difference around midnight cannot make this flaky.
        jdbcTemplate.update("update play_availability set available_until = ?", LocalDate.now().minusDays(2));

        mockMvc.perform(get("/api/games/3328/available-players").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void availabilityShowsOnTheLibraryEntryAndCanBeRemoved() throws Exception {
        setAvailability(luigi, "{\"platforms\":[\"pc\"],\"languages\":[\"en\"]}");

        mockMvc.perform(get("/api/me/library").header(HttpHeaders.AUTHORIZATION, bearer(luigi)))
                .andExpect(jsonPath("$.items[0].availability.platforms[0]").value("pc"));

        mockMvc.perform(delete("/api/me/library/3328/availability").header(HttpHeaders.AUTHORIZATION, bearer(luigi)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/games/3328/available-players").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void pastDateAndEmptyPlatformsAreRejected() throws Exception {
        mockMvc.perform(put("/api/me/library/3328/availability").header(HttpHeaders.AUTHORIZATION, bearer(luigi))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platforms\":[],\"languages\":[\"en\"],\"availableUntil\":\"2000-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)));
    }

    @Test
    void availabilityNeedsTheGameInLibrary() throws Exception {
        mockMvc.perform(put("/api/me/library/1/availability").header(HttpHeaders.AUTHORIZATION, bearer(luigi))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"platforms\":[\"pc\"],\"languages\":[\"en\"]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void matchmakingRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/games/3328/available-players")).andExpect(status().isUnauthorized());
    }

    @Test
    void deletedAccountsLeaveMatchmaking() throws Exception {
        setAvailability(luigi, "{\"platforms\":[\"pc\"],\"languages\":[\"en\"]}");
        mockMvc.perform(delete("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(luigi)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/games/3328/available-players").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(jsonPath("$.totalItems").value(0));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=MatchmakingIntegrationTest`
Expected: FAIL (missing endpoints).

- [ ] **Step 3: Implement availability**

`library/AvailabilityRequest.java`
```java
package it.mygamingjournal.library;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record AvailabilityRequest(
        @NotEmpty @Size(max = 10) List<@Pattern(regexp = "^[a-z0-9-]+$", message = "must be a platform slug") String> platforms,
        @NotEmpty @Size(max = 10) List<@Pattern(regexp = "^[a-z]{2}$", message = "must be an ISO 639-1 code") String> languages,
        @FutureOrPresent LocalDate availableUntil) {
}
```

`library/AvailabilityService.java`
```java
package it.mygamingjournal.library;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final LibraryService library;
    private final PlayAvailabilityRepository availabilities;

    @Transactional
    public AvailabilityResponse set(long userId, long rawgGameId, AvailabilityRequest request) {
        GameEntry entry = library.requireEntry(userId, rawgGameId);
        PlayAvailability availability = availabilities.findById(entry.getId())
                .orElseGet(() -> new PlayAvailability(entry.getId()));
        availability.setPlatforms(new ArrayList<>(request.platforms().stream().distinct().toList()));
        availability.setLanguages(new ArrayList<>(request.languages().stream().distinct().toList()));
        availability.setAvailableUntil(request.availableUntil());
        return AvailabilityResponse.from(availabilities.save(availability));
    }

    @Transactional
    public void clear(long userId, long rawgGameId) {
        availabilities.deleteById(library.requireEntry(userId, rawgGameId).getId());
    }
}
```

Modify `library/LibraryController.java`: add the field `private final AvailabilityService availability;` and these methods:
```java
    @PutMapping("/{rawgGameId}/availability")
    public AvailabilityResponse setAvailability(@AuthenticationPrincipal AuthUser me,
            @PathVariable @Positive long rawgGameId, @Valid @RequestBody AvailabilityRequest request) {
        return availability.set(me.id(), rawgGameId, request);
    }

    @DeleteMapping("/{rawgGameId}/availability")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearAvailability(@AuthenticationPrincipal AuthUser me, @PathVariable @Positive long rawgGameId) {
        availability.clear(me.id(), rawgGameId);
    }
```

- [ ] **Step 4: Implement matchmaking**

`matchmaking/AvailablePlayerResponse.java`
```java
package it.mygamingjournal.matchmaking;

import it.mygamingjournal.user.UserSummary;

import java.time.LocalDate;
import java.util.List;

public record AvailablePlayerResponse(UserSummary user, List<String> platforms, List<String> languages,
                                      LocalDate availableUntil) {
}
```

`matchmaking/MatchmakingService.java`
```java
package it.mygamingjournal.matchmaking;

import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import it.mygamingjournal.user.UserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Plain SQL: Postgres array membership (= any) reads far better here than JPQL. */
@Service
@RequiredArgsConstructor
public class MatchmakingService {

    private static final String FROM_WHERE = """
            from play_availability a
            join game_entries e on e.id = a.entry_id
            join users u on u.id = e.user_id
            where e.rawg_game_id = :gameId
              and u.id <> :me
              and (a.available_until is null or a.available_until >= :today)
              and (cast(:platform as text) is null or cast(:platform as text) = any(a.platforms))
              and (cast(:language as text) is null or cast(:language as text) = any(a.languages))
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<AvailablePlayerResponse> findAvailablePlayers(long me, long rawgGameId, String platform,
                                                                      String language, PageParams paging) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("gameId", rawgGameId)
                .addValue("me", me)
                .addValue("today", LocalDate.now(clock))
                .addValue("platform", platform, Types.VARCHAR)
                .addValue("language", language, Types.VARCHAR)
                .addValue("limit", paging.pageSize())
                .addValue("offset", paging.offset());

        Long total = jdbc.queryForObject("select count(*) " + FROM_WHERE, params, Long.class);
        List<AvailablePlayerResponse> items = jdbc.query("""
                select u.id, u.username, u.display_name, u.avatar_url, u.level,
                       a.platforms, a.languages, a.available_until
                """ + FROM_WHERE + " order by lower(u.username) limit :limit offset :offset", params,
                (rs, rowNum) -> new AvailablePlayerResponse(
                        new UserSummary(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"),
                                rs.getString("avatar_url"), rs.getInt("level")),
                        toList(rs.getArray("platforms")),
                        toList(rs.getArray("languages")),
                        rs.getObject("available_until", LocalDate.class)));
        return new PageResponse<>(items, paging.page(), paging.pageSize(), total == null ? 0 : total);
    }

    private static List<String> toList(Array array) throws SQLException {
        return List.of((String[]) array.getArray());
    }
}
```

`matchmaking/MatchmakingController.java`
```java
package it.mygamingjournal.matchmaking;

import it.mygamingjournal.auth.AuthUser;
import it.mygamingjournal.common.paging.PageParams;
import it.mygamingjournal.common.paging.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MatchmakingController {

    private final MatchmakingService matchmaking;

    @GetMapping("/api/games/{rawgGameId}/available-players")
    public PageResponse<AvailablePlayerResponse> availablePlayers(@AuthenticationPrincipal AuthUser me,
            @PathVariable @Positive long rawgGameId,
            @RequestParam(required = false) @Pattern(regexp = "^[a-z0-9-]+$") String platform,
            @RequestParam(required = false) @Pattern(regexp = "^[a-z]{2}$") String language,
            @Valid PageParams paging) {
        return matchmaking.findAvailablePlayers(me.id(), rawgGameId, platform, language, paging);
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./mvnw test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: add play availability and matchmaking

<trailer>"
```

---

### Task 12: OpenAPI, Docker image, CI and README

**Files:**
- Create: `src/main/java/it/mygamingjournal/config/OpenApiConfig.java`, `Dockerfile`, `.dockerignore`, `.github/workflows/ci.yml`
- Rewrite: `README.md`
- Test: `src/test/java/it/mygamingjournal/config/OpenApiIntegrationTest.java`

**Interfaces:**
- Consumes: every controller (documented automatically by springdoc).
- Produces: `/v3/api-docs` and `/swagger-ui.html` in `dev`; container image; CI workflow.

- [ ] **Step 1: Write the failing test**

`src/test/java/it/mygamingjournal/config/OpenApiIntegrationTest.java`
```java
package it.mygamingjournal.config;

import it.mygamingjournal.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
class OpenApiIntegrationTest extends IntegrationTest {

    @Test
    void apiDocsDescribeTheApiAndTheBearerScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("MyGamingJournal API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/me/library/{rawgGameId}']").exists())
                .andExpect(jsonPath("$.paths['/api/games/{rawgGameId}/available-players']").exists());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=OpenApiIntegrationTest`
Expected: FAIL (`$.info.title` is springdoc's default "OpenAPI definition"; no `bearerAuth` scheme).

- [ ] **Step 3: Implement the OpenAPI configuration**

`config/OpenApiConfig.java`
```java
package it.mygamingjournal.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MyGamingJournal API")
                        .version("2.0")
                        .description("User data, community features and a cached proxy to the RAWG video games "
                                + "database. Game data by RAWG (https://rawg.io)."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=OpenApiIntegrationTest`
Expected: PASS.

- [ ] **Step 5: Add the Docker image**

`.dockerignore`
```
target/
.git/
.idea/
.env
```

`Dockerfile`
```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --no-create-home app
USER app
COPY --from=build /app/target/mygamingjournal-api.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Run: `docker build -t mygamingjournal-api .`
Expected: image builds successfully.

- [ ] **Step 6: Add CI**

`.github/workflows/ci.yml`
```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Build and test
        run: chmod +x mvnw && ./mvnw -B verify
```

- [ ] **Step 7: Rewrite `README.md`**

````markdown
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
````

- [ ] **Step 8: Run the full build**

Run: `./mvnw verify`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 9: Smoke-test the dev profile**

Run: `./mvnw spring-boot:run`. In another shell: `curl -s http://localhost:8080/api/genres`.
Expected: the app starts and Docker Compose brings up Postgres. Without a key the answer is a 503 ProblemDetail with `"detail":"RAWG API key not configured"`; with a key it is a JSON array of genres. Stop the app afterwards.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "chore: add OpenAPI config, Dockerfile, CI and README

<trailer>"
```
