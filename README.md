# Distributed Rate Limiter

A learning project for SDE-1 / Software Engineer 1 preparation. It will enforce per-client API rate limits across multiple gateway instances using a **Token Bucket** stored in Redis and evaluated atomically with a Lua script.

**Current status:** Milestone 3 — Redis Lua Token Bucket in `rate-limiter-core`. Gateway filters, the dashboard, and deployment are **planned, not implemented**. The admin API and snapshot endpoint have no authentication yet (Milestone 7).

| Area | Status |
|---|---|
| Maven multi-module skeleton | Implemented |
| Spring Boot entry points | Implemented |
| PostgreSQL 16 + Redis 7 via Docker Compose | Implemented and verified (see Testing) |
| Plans, clients, API keys, and policies in PostgreSQL | Implemented (Milestone 1) |
| Admin API on `config-service` | Implemented (Milestone 1). Open locally; do not expose it publicly |
| `GET /internal/v1/snapshot` and ETag | Implemented (Milestone 2). Open locally; internal token is Milestone 7 |
| Token Bucket / Redis Lua | Implemented in `rate-limiter-core` (Milestone 3). The gateway does not call it yet |
| Gateway filters and snapshot refresh | Planned (Milestones 5–6) |
| Security, dashboard, metrics, two-gateway load test, EC2 | Planned (Milestones 7–11) |

---

## Project overview

### What this project does (planned)

API clients will send requests through an API gateway with an `X-API-Key` header. The gateway will identify the client, choose the most specific rate-limit policy for that client’s plan, and ask Redis whether a token is available. Allowed requests are forwarded to a demo backend. Excess requests receive `429 Too Many Requests`.

### Objectives

- Learn backend fundamentals by building a small, realistic distributed system.
- Practice OOP, REST, SQL, concurrency, caching, and system-design trade-offs that SDE-1 interviews expect.
- Keep the architecture simple: three Spring Boot apps, one shared limiter library, PostgreSQL for configuration, Redis for hot state.

### Problems it will solve

- **Unfair or abusive traffic:** one client cannot consume unbounded backend capacity.
- **Inconsistent limits across instances:** two gateways will share one Redis bucket per client and policy, so they cannot each grant a full burst.
- **Race conditions:** Redis runs the read-refill-consume-write steps in one Lua script so two requests cannot both spend the last token.

### Intended use cases

- Server-to-server API clients (not browsers calling the gateway).
- An admin managing plans, policies, clients, and API keys from a dashboard.
- Local development and a later single-host Docker Compose / EC2 demo.

---

## Architecture

**Implemented now:** Configuration Service admin API and `GET /internal/v1/snapshot`, plus PostgreSQL and Redis on a private Docker network. The gateway does not poll the snapshot yet.

**Planned runtime:**

```mermaid
flowchart LR
    Client --> Nginx[nginx]
    Nginx --> GW1[Gateway instance 1]
    Nginx --> GW2[Gateway instance 2]
    Dashboard[React dashboard] --> Config[Configuration Service]
    GW1 --> Redis[(Redis token buckets)]
    GW2 --> Redis
    Config --> Redis
    Config --> PG[(PostgreSQL)]
    GW1 --> Demo[Demo service]
    GW2 --> Demo
```

### Component responsibilities

| Component | Role | Status |
|---|---|---|
| **Gateway** | Authenticate API keys, apply Token Bucket, forward to the demo service | Skeleton only |
| **rate-limiter-core** | Redis Lua Token Bucket library used by the gateway | Implemented (Milestone 3). Not wired into the gateway yet |
| **Configuration Service** | Admin CRUD, PostgreSQL, and the snapshot endpoint | Admin API and snapshot endpoint implemented |
| **snapshot-api** | Shared snapshot records (no Redis / HTTP) | Records implemented |
| **Demo service** | Tiny in-memory backend behind the gateway | Skeleton only |
| **PostgreSQL 16** | Durable plans, clients, keys, policies | Schema applied by Flyway when `config-service` starts |
| **Redis 7** | Token buckets and later metrics | Bucket library implemented. Metrics are still Milestone 9. Compose Redis is local infra only |
| **React dashboard** | Admin UI | Placeholder directory |
| **nginx** | TLS, same-origin admin, public `/api/` | Placeholder directory |

### Planned request flow

1. Client calls `GET /api/...` with `X-API-Key`.
2. Gateway matches a route, hashes the key, loads the client from an in-memory snapshot.
3. Gateway picks the most specific policy and calls Redis (`EVALSHA`).
4. If allowed, it forwards the request (API key stripped, `X-Client-Id` set). If not, it returns `429`.

The gateway will **not** query PostgreSQL per request. It will refresh a snapshot from the Configuration Service about every 10 seconds.

### Maven modules

| Module | Depends on | Must not depend on |
|---|---|---|
| `snapshot-api` | `jackson-annotations` (JSON field order only) | Spring Web, JPA, Redis |
| `rate-limiter-core` | `spring-data-redis` and Lettuce (optional, so they are not forced onto the gateway) | HTTP, MVC, JPA, `config-service` |
| `config-service` | `snapshot-api` | `rate-limiter-core` |
| `gateway` | `snapshot-api`, `rate-limiter-core` | PostgreSQL / JPA |
| `demo-service` | — | Limiter and config modules |

---

## Technology stack

| Technology | Why we use it |
|---|---|
| **Java 21** | Language you already know; virtual threads later for blocking Redis calls |
| **Maven multi-module** | One repo, one version, clear module boundaries |
| **Spring Boot 3.5.16** | Standard way to build production Java services |
| **Spring Cloud 2025.0.3** | Official release train for Boot 3.5.x ([compatibility table](https://spring.io/projects/spring-cloud)) |
| **Spring Cloud Gateway Server MVC** | Servlet / thread-per-request gateway (`spring-cloud-starter-gateway-server-webmvc`), not the reactive WebFlux gateway |
| **PostgreSQL 16** | Source of truth for configuration |
| **Redis 7** | Shared, atomic limiter state (`TIME` + Lua) |
| **Docker Compose** | One-command local Postgres and Redis |
| **React + Vite** | Future admin UI (Milestone 8) |
| **nginx** | Future TLS and reverse proxy (Milestone 8+) |
| **AWS EC2** | Future single-host deploy (Milestone 11) |

**Intentionally out of scope:** Eureka, Spring Cloud Config Server, Kafka, Kubernetes, Redis Cluster, a separate rate-limiter microservice.

---

## Key features

All of the following are **planned** unless marked implemented.

- Token Bucket rate limiting with lazy refill — **implemented in `rate-limiter-core` (Milestone 3)**. Gateway enforcement is Milestone 5
- Atomic Redis Lua (`EVALSHA`, write on allow only) — **implemented in `rate-limiter-core` (Milestone 3)**
- Two gateway instances sharing Redis — *planned* (Milestone 10)
- API-key authentication (`X-API-Key`, SHA-256 stored) — *planned*
- Plans and policies in PostgreSQL — **implemented (Milestone 1)**
- Admin API for plans, policies, clients, and API keys — **implemented (Milestone 1)**
- Configuration snapshots + `ETag` / `304` — **implemented (Milestone 2)**. Gateway polling is still planned (Milestone 6)
- Admin dashboard — *planned*
- Best-effort metrics flushed to Redis — *planned*
- Maven multi-module + Compose infra — **implemented (Milestone 0)**

---

## Algorithm

Token Bucket, lazy refill. The production math is only in `rate-limiter-core/src/main/resources/redis/token_bucket.lua`. Java loads that script with Spring Data Redis, which sends `EVALSHA` and reloads it after `NOSCRIPT`.

- Each client+policy has a Redis hash `rl:bucket:<clientId>:<policyId>` with `tokens` and `ts`.
- On a request, the script uses Redis `TIME`, refills `min(capacity, tokens + elapsed × rate)`, and consumes one token if available. The first request starts from a full bucket.
- Redis executes the script atomically (single-threaded), which prevents two gateways from over-admitting.
- Denied requests do not write and do not refresh the TTL. Idle keys expire after the time to refill from empty to full, plus 1 second.
- The script returns integers: allowed, remaining, retry-after milliseconds, and reset milliseconds. A Redis outage returns `UNAVAILABLE` from the library. Mapping that to HTTP fail-open is the gateway’s job (Milestone 5).

The gateway does not call this library yet.

---

## API documentation

### Implemented

`config-service` on port **8081** serves the admin API and `GET /internal/v1/snapshot`. Neither is authenticated yet. Run the service only on localhost, with Postgres already up.

| Method and path | Success |
|---|---|
| `GET /api/v1/admin/plans` | `200` |
| `POST /api/v1/admin/plans` | `201` and `Location`. Creates the plan and its default `/**` + `ANY` policy together |
| `GET /api/v1/admin/plans/{planId}` | `200` |
| `PATCH /api/v1/admin/plans/{planId}` | `200`. Name and description only |
| `DELETE /api/v1/admin/plans/{planId}` | `204`, or `409` when clients still use it |
| `GET /api/v1/admin/plans/{planId}/policies` | `200` |
| `POST /api/v1/admin/plans/{planId}/policies` | `201` |
| `GET /api/v1/admin/plans/{planId}/policies/{policyId}` | `200` |
| `PUT /api/v1/admin/plans/{planId}/policies/{policyId}` | `200`. Body includes `version`. Stale version is `409` |
| `DELETE /api/v1/admin/plans/{planId}/policies/{policyId}` | `204`, or `409` for the default policy |
| `GET /api/v1/admin/clients?planId=&status=&page=0&size=20` | `200` page |
| `POST /api/v1/admin/clients` | `201` |
| `GET /api/v1/admin/clients/{clientId}` | `200` |
| `PATCH /api/v1/admin/clients/{clientId}` | `200`. Name, plan, or `ACTIVE` / `SUSPENDED` |
| `GET /api/v1/admin/clients/{clientId}/api-keys` | `200`. Prefix and dates only |
| `POST /api/v1/admin/clients/{clientId}/api-keys` | `201`, raw key once, `Cache-Control: no-store` |
| `POST /api/v1/admin/clients/{clientId}/api-keys/{keyId}/revoke` | `200`. Safe to call again |
| `GET /internal/v1/snapshot` | `200` and `ETag`, or `304` when `If-None-Match` matches |

Errors use `application/problem+json` with a stable `code` (`VALIDATION_FAILED`, `NOT_FOUND`, `DUPLICATE`, `PLAN_IN_USE`, `DEFAULT_POLICY_PROTECTED`, `POLICY_VERSION_CONFLICT`, `CLIENT_SUSPENDED`, `INTERNAL_ERROR`). If PostgreSQL cannot be reached, the snapshot endpoint returns `503` with `INTERNAL_ERROR`.

The snapshot body is configuration only. There is no `generatedAt`. `plans` lists each plan id and code with its enabled policies (`policyId`, `pathPattern`, `httpMethod`, `capacity`, `refillTokens`, `refillPeriodSeconds`). `apiKeys` lists non-revoked keys, including keys of suspended clients, as `keyHash`, `clientId`, `planId`, and `clientStatus`. The `ETag` is a strong quoted SHA-256 of those exact response bytes. Send it back as `If-None-Match` to receive `304` with an empty body when nothing changed.

| Application | Default local port | Endpoints |
|---|---|---|
| `gateway` | 8080 | None implemented |
| `config-service` | 8081 | Admin API and `GET /internal/v1/snapshot` |
| `demo-service` | 8082 | None implemented |

### Planned (do not call these yet)

- Gateway public traffic: `/api/**` with `X-API-Key`

---

## Database design

Flyway migration `config-service/src/main/resources/db/migration/V1__init.sql` creates the tables. Hibernate uses `ddl-auto: validate`, so the application refuses to start if the entities and the schema disagree.

| Table | Purpose |
|---|---|
| `plans` | Tiers. Seeded `FREE`, `PRO`, and `ENTERPRISE` |
| `clients` | API customers assigned to a plan. `ACTIVE` or `SUSPENDED` |
| `api_keys` | SHA-256 hashes and display prefixes. `revoked_at` revokes a key without deleting the row |
| `rate_limit_policies` | Path, method (`ANY`, `GET`, `HEAD`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`), capacity, refill, and `@Version` |

Seeded default policies (`/**`, method `ANY`):

| Plan | Capacity | Refill tokens | Period |
|---|---|---|---|
| FREE | 20 | 100 | 60 seconds |
| PRO | 100 | 600 | 60 seconds |
| ENTERPRISE | 1000 | 6000 | 60 seconds |

The default policy can be edited. The database rejects disabling it. The service rejects deleting it.

---

## Configuration

Copy `.env.example` to `.env` and change the passwords.

| Variable | Purpose | Used in Milestone 0 |
|---|---|---|
| `POSTGRES_DB` | Database name | Yes |
| `POSTGRES_USER` | Database user | Yes |
| `POSTGRES_PASSWORD` | Database password | Yes |
| `REDIS_PASSWORD` | Redis `requirepass` | Yes |
| `REDIS_MAXMEMORY` | Redis memory cap (default `256mb`) | Yes |
| `INTERNAL_TOKEN` | Gateway ↔ config-service shared secret | Future |
| `ADMIN_USERNAME` | Dashboard admin | Future |
| `ADMIN_PASSWORD_BCRYPT` | Bcrypt hash of admin password | Future |
| `GATEWAY_INSTANCE_ID` | Distinguishes `gw-1` / `gw-2` | Future |

Never commit `.env`. Compose interpolates variables from `--env-file`; it does not hardcode secrets.

---

## Local setup

### Prerequisites

- Java 21
- Maven Wrapper is committed (`./mvnw`); a local Maven install is optional
- Docker Desktop (or another Docker Engine + Compose v2)
- Git
- Node.js — **future** (dashboard, Milestone 8)

If `java` is not on your `PATH`, set `JAVA_HOME` to a JDK 21 installation.

### Environment file

```bash
cp .env.example .env
# Edit POSTGRES_PASSWORD and REDIS_PASSWORD in .env
```

---

## Execution instructions

Commands below assume the **repository root**.

### Start PostgreSQL and Redis (IDE-accessible ports)

This uses the **override** file so you can reach `localhost:5432` and `localhost:6379` from IntelliJ / DataGrip / `redis-cli`.

```bash
docker compose --env-file .env \
  -f infrastructure/docker/docker-compose.yml \
  -f infrastructure/docker/docker-compose.infra.yml \
  up -d
```

### Start infrastructure without publishing ports

Use this when you only want containers on the Docker network (full-stack style):

```bash
docker compose --env-file .env \
  -f infrastructure/docker/docker-compose.yml \
  up -d
```

### Validate Compose files

```bash
docker compose --env-file .env \
  -f infrastructure/docker/docker-compose.yml \
  config

docker compose --env-file .env \
  -f infrastructure/docker/docker-compose.yml \
  -f infrastructure/docker/docker-compose.infra.yml \
  config
```

### Stop infrastructure

```bash
docker compose --env-file .env \
  -f infrastructure/docker/docker-compose.yml \
  -f infrastructure/docker/docker-compose.infra.yml \
  down
```

Add `-v` only if you also want to delete the Postgres volume.

### Build and unit-test (no Docker required)

```bash
./mvnw -version
./mvnw test
```

`./mvnw verify` runs Testcontainers integration tests (`*IT`). Docker must be running. `./mvnw test` does not start containers.

### Run a Spring Boot app from the IDE (optional)

Start infra with the override first. `config-service` connects to `localhost:5432` and applies Flyway on startup. `gateway` and `demo-service` still do not use Postgres or Redis. Override the database password with `POSTGRES_PASSWORD` when `.env` is not `change-me`.

---

## Testing

### Current tests

`./mvnw test` runs unit tests and the gateway and demo context tests. It does not need Docker.

`./mvnw verify` also runs Testcontainers integration tests: `rate-limiter-core` `TokenBucketIT` on Redis 7, and `config-service` `AdminApiIT` and `SnapshotApiIT` on PostgreSQL 16.

Verified on 2026-11-05 (Java 21.0.13.0.3, Maven 3.10.0):

| Module | `./mvnw test` | `./mvnw verify` extra |
|---|---|---|
| snapshot-api | 0 | none |
| rate-limiter-core | 10 unit tests | 12 integration tests, Redis 7 |
| config-service | 15 unit tests | 14 integration tests, PostgreSQL 16 |
| gateway | 1 context test | none |
| demo-service | 1 context test | none |

`./mvnw test` and `./mvnw verify`: **BUILD SUCCESS**. `rate-limiter-core` verify adds 12 Redis 7 tests, including the concurrent bucket test. config-service verify adds 14 PostgreSQL 16 tests: 7 admin tests and 7 snapshot tests. Details are in [PROJECT_PLAN.md](PROJECT_PLAN.md).

### Future strategy (approved)

- **Surefire / `mvn test`:** unit tests (matching, validation). No Docker.
- **Failsafe / `mvn verify`:** Testcontainers Redis 7 and PostgreSQL 16 (`*IT`).
- **Lua is the only production Token Bucket math.** Concurrent `CountDownLatch` tests against real Redis.
- **Compose smoke suite** and **k6** under `load-tests/` in later milestones.
- Two-gateway over-admission test is required before claiming “distributed” (Milestone 10).

---

## Deployment

**Pending.** Milestone 11 covers TLS at nginx and AWS EC2. There are no deploy scripts or cloud resources in this milestone.

---

## Project plan

See [PROJECT_PLAN.md](PROJECT_PLAN.md) for milestones, dependencies, and interview checkpoints.

---

## License

Educational / personal project. Add a license file if you publish the repository.
