# Project plan

Frozen architecture pack: Sections 1–10 (approved). This file tracks implementation only.

**Status key:** unimplemented until a checkbox is marked after real verification.

---

## Approved architecture decisions (do not reopen in code)

- Rate limiter is a **Maven library** (`rate-limiter-core`), not a microservice.
- Gateway is **Spring Cloud Gateway Server MVC** + Java 21 virtual threads (virtual threads enabled in Milestone 5).
- Custom Redis 7 **Lua Token Bucket**; optional later comparison with built-in `RedisRateLimiter`.
- Start with one gateway; **two instances** for distributed proof (Milestone 10).
- **Out of scope:** Eureka, Spring Cloud Config Server, Kafka, Kubernetes, Redis Cluster.
- Policies in PostgreSQL; gateway uses an **in-memory snapshot** with periodic refresh (no per-request SQL).
- Identity: `X-API-Key`; SHA-256 stored; many keys per client share one `clientId` budget.
- Plans + most-specific policy; default `/**` + `ANY` per plan (protected).
- Fail-open on Redis **never** skips authentication or client-status checks.
- Metrics are **best-effort** (lost Redis ACK can double-count); isolated from allow/deny.
- Milestone 10 depends on the **gateway request path, snapshot refresh, and Compose** — not the dashboard or metrics.

---

## Milestone dependencies

```text
0 ─┬─► 1 ─► 2 ─┐
   └─► 3 ──────┼─► 5 ─► 6 ─► 7 ─► 8 ─► 9
          4 ───┘              │
                              └─► 10 (needs 5, 6, Compose; not 8 or 9) ─► 11
                                                                         └─► 12 (optional)
```

- Milestones 1–2 and 3 proceed **in parallel** after Milestone 0.
- Milestone 5 requires 2, 3, and 4.
- Milestone 10 requires 5, 6, and Compose with two gateways on one Redis.
- Milestone 11 follows the distributed proof for a distributed deployment claim.

---

## Milestone 0: Repository skeleton and local infrastructure

**Objective.** Compilable multi-module repo, Maven Wrapper, Postgres 16 + Redis 7, docs that match the frozen pack.

**Dependencies.** None.

**Implementation tasks**

- [x] Parent POM (Java 21, Spring Boot 3.5.16, Spring Cloud 2025.0.3)
- [x] Modules: `snapshot-api`, `rate-limiter-core`, `config-service`, `gateway`, `demo-service`
- [x] Approved dependency boundaries
- [x] Spring Boot entry points only (no business logic)
- [x] Maven Wrapper (3.10.0, only-script)
- [x] Compose: Postgres + Redis on `backend` network, no published ports in the base file
- [x] Compose override publishes `5432` and `6379` for IDE use
- [x] `.env.example`, gitignored `.env`
- [x] `README.md` and this plan

**Required tests / verification**

- [x] `./mvnw -version`
- [x] `./mvnw test` succeeds
- [x] `docker compose … config` for both Compose configurations
- [x] Postgres and Redis health checks pass
- [x] Local connections work with the override (ports 5432 and 6379)
- [x] `.env` is ignored by Git

**Completion criteria.** Modules compile; infra runs; no Milestone 1+ features; docs do not claim unimplemented work.

**Interview checkpoints.** Why a multi-module Maven build; why config-service must not depend on `rate-limiter-core`; why Redis/Postgres stay off the public network; env files vs hardcoded secrets.

**Verification notes (2026-11-05)**

- `./mvnw -version`: Apache Maven 3.10.0, Java 21.0.13.0.3 (Oracle)
- `./mvnw test`: **BUILD SUCCESS**. `contextLoads` — config-service 1/0/0, gateway 1/0/0, demo-service 1/0/0. Library modules have no tests.
- Base Compose `config`: no published ports for Postgres/Redis.
- Infra override `config`: `5432` and `6379` published.
- After `up` with override: both containers `healthy` on try 4; `pg_isready` accepting connections; `psql` connected as `ratelimiter`/`ratelimiter`; Redis `PONG` with password; wrong password → `WRONGPASS`; host `localhost:5432` and `localhost:6379` reachable.
- `git check-ignore -v .env` → `.gitignore:2:.env`. `.env.example` is not ignored.
- Containers were stopped after verification (`down`, volume kept).

---

## Milestone 1: Configuration Service — schema and admin API

**Objective.** Persist plans, clients, keys, and policies. Admin HTTP works without the gateway.

**Dependencies.** Milestone 0.

**Implementation tasks**

- [x] Flyway `V1__init.sql` (approved columns, `'ANY'`, `HEAD`/`OPTIONS`, default-policy `CHECK`, `@Version`)
- [x] JPA + feature packages + record DTOs
- [x] Admin endpoints for plans, policies, clients, API keys
- [x] Plan create + default policy in one transaction; default cannot be deleted
- [x] Seed FREE / PRO / ENTERPRISE
- [x] No Spring Security yet (Milestone 7)

**Required tests**

- [x] Testcontainers PostgreSQL 16: Flyway, unique constraint, default disable, `RESTRICT`, optimistic `409`, hash-only keys, revoke idempotency, `Cache-Control: no-store`

**Completion criteria.** Admin CRUD works against Postgres; constraints match the frozen schema.

**Interview checkpoints.** 3NF; `NULL` vs `'ANY'` in `UNIQUE`; optimistic locking; SHA-256 for API keys vs bcrypt for passwords.

**Verification notes (2026-11-05)**

- `./mvnw test`: **BUILD SUCCESS**. config-service 6 unit tests (hash, path pattern, default-policy rule). gateway 1, demo-service 1. No Docker.
- `./mvnw verify`: **BUILD SUCCESS**. `AdminApiIT` 7 tests on Testcontainers PostgreSQL 16.15. Flyway `V1` applied; `ddl-auto=validate` accepted the schema. Covered unique `ANY`, default-policy check and delete protection, `RESTRICT` / cascade, stale `version` → `409`, hash-only key storage, revoke idempotency, and `Cache-Control: no-store`.
- Spring Security is not on the classpath. Snapshot and Redis code were not added.

---

## Milestone 2: Snapshot API and ETag

**Objective.** `GET /internal/v1/snapshot` returns deterministic JSON and a content hash `ETag`.

**Dependencies.** Milestone 1.

**Implementation tasks**

- [ ] `snapshot-api` records (no `generatedAt`)
- [ ] Two ordered queries in one `READ ONLY REPEATABLE READ` transaction
- [ ] Serialize once → SHA-256 those bytes → send those bytes
- [ ] `If-None-Match` → `304`
- [ ] Thin `X-Internal-Token` check (hardened in Milestone 7)

**Required tests**

- [ ] Stable `ETag` when data is unchanged; contents rules (enabled policies, revoked omitted, suspended keys + `clientStatus`)

**Completion criteria.** Gateway-ready snapshot contract; `304` works.

**Interview checkpoints.** Why `generatedAt` would break `304`; why `MAX(updated_at)` is wrong; MVCC / `REPEATABLE READ`.

---

## Milestone 3: rate-limiter-core and Redis Lua Token Bucket

**Objective.** Atomic Token Bucket in Redis. Highest technical priority.

**Dependencies.** Milestone 0 (not the config service).

**Implementation tasks**

- [ ] `token_bucket.lua` (Redis `TIME`, write on allow only, integer returns, TTL)
- [ ] Java `EVALSHA` wrapper, Lettuce, 100 ms / 500 ms timeouts
- [ ] Key `rl:bucket:<clientId>:<policyId>`
- [ ] Redis unavailable → `UNAVAILABLE` (HTTP fail-open is the gateway’s job)

**Required tests**

- [ ] **Priority:** concurrent `CountDownLatch`, allows == tokens available
- [ ] Burst, deny-does-not-write, short-period refill, capacity clamp, TTL
- [ ] Connection refused → `UNAVAILABLE`
- [ ] No Java production copy of the refill math

**Completion criteria.** Core is correct against Redis 7 Testcontainers.

**Interview checkpoints.** Check-then-act; JVM locks vs Redis atomicity; lazy refill.

---

## Milestone 4: Demo service

**Objective.** In-memory backend the gateway can forward to.

**Dependencies.** Milestone 0.

**Implementation tasks**

- [ ] Small in-memory HTTP API (for example orders)
- [ ] No second database; not published in full-stack Compose

**Required tests**

- [ ] Web slice / context tests for the demo endpoints

**Completion criteria.** Demo responds locally; no limiter logic.

**Interview checkpoints.** Why backends trust `X-Client-Id` only when they are not public.

---

## Milestone 5: Gateway request path

**Objective.** One gateway enforces identity, limits, and forwarding.

**Dependencies.** Milestones 2, 3, and 4.

**Implementation tasks**

- [ ] Virtual threads; shared Lettuce and HTTP clients
- [ ] Filters: request id → API key → policy match → `tryConsume` → forward
- [ ] Approved status codes and rate-limit headers
- [ ] Fail-open only after snapshot auth/status checks

**Required tests**

- [ ] Redis + MockWebServer/WireMock: 401/403/429/200, header hygiene, Redis down + invalid key still 401

**Completion criteria.** Happy path and fail-open-with-auth work on one instance.

**Interview checkpoints.** One Redis RTT on the hot path; Remaining vs Reset vs Retry-After.

---

## Milestone 6: Snapshot refresh, validation, and readiness

**Objective.** Approved configuration propagation.

**Dependencies.** Milestone 5.

**Implementation tasks**

- [ ] ~10 s fixed-delay poll + jitter; timeouts
- [ ] `ETag` / `304`; update `lastSuccessfulRefreshAt` on 200 and 304
- [ ] All-or-nothing validation; one `AtomicReference` read per request
- [ ] Readiness = snapshot loaded, not Redis up
- [ ] Keep last snapshot on refresh failure

**Required tests**

- [ ] Invalid snapshot rejected; 304 updates timestamp; readiness UP if Redis is down; `503` before first snapshot

**Completion criteria.** Refresh and health match the frozen pack.

**Interview checkpoints.** Eventual consistency (~10–12 s typical); liveness vs readiness; revoked keys during a config outage.

---

## Milestone 7: Security

**Objective.** Protect admin and internal APIs.

**Dependencies.** Milestones 1–2 and 6.

**Implementation tasks**

- [ ] Session login, CSRF, `HttpOnly` / `SameSite` cookie
- [ ] One bcrypt admin from environment variables
- [ ] Constant-time `X-Internal-Token`
- [ ] Logging redaction

**Required tests**

- [ ] CSRF; unauthenticated admin denied; bad internal token denied

**Completion criteria.** Admin and snapshot endpoints reject anonymous callers.

**Interview checkpoints.** CSRF; bcrypt vs SHA-256; timing attacks.

---

## Milestone 8: React dashboard and nginx

**Objective.** Same-origin admin UI; public `/api/` via nginx. Ports 80/443 only in full-stack Compose.

**Dependencies.** Milestone 7.

**Implementation tasks**

- [ ] React + JavaScript + Vite; dev proxy
- [ ] Production static files in the nginx image
- [ ] nginx routes; block `/internal` and `/actuator`; `limit_req` on login
- [ ] Frontend and backend Docker networks

**Required tests**

- [ ] Thin Compose smoke (login). Browser E2E optional.

**Completion criteria.** Dashboard talks only to the Configuration Service through nginx.

**Interview checkpoints.** Same-origin vs CORS; reverse proxy.

---

## Milestone 9: Best-effort metrics

**Objective.** Allowed / denied / fail-open totals and per-policy counts.

**Dependencies.** Milestones 5 and 8.

**Implementation tasks**

- [ ] Micrometer counters; flush deltas to `rl:metrics:*` about every 5 s
- [ ] Metrics failures never affect `tryConsume`
- [ ] Admin metrics endpoints
- [ ] Snapshot age via logs/Actuator only (Redis ages optional in Milestone 10)

**Required tests**

- [ ] Happy-path flush; metrics Redis failure does not change allow/deny
- [ ] Do **not** claim exactly-once delivery

**Completion criteria.** Dashboard can read Redis metric hashes.

**Interview checkpoints.** Cardinality; lost-ACK double-count.

---

## Milestone 10: Two gateway instances and distributed load testing

**Objective.** Prove distributed correctness. Resume claim depends on this milestone.

**Dependencies.** Milestones **5 and 6** plus Compose (two gateways, shared Redis). **Does not** require Milestones 8 or 9.

**Implementation tasks**

- [ ] `gateway-2`, same image, `GATEWAY_INSTANCE_ID`, nginx upstream or equivalent
- [ ] Optional snapshot-age keys in Redis
- [ ] k6 scripts in `load-tests/`

**Required tests**

- [ ] Two-gateway burst: allows ≤ capacity (release bar)
- [ ] Fail-open does not skip auth
- [ ] Latency with healthy Redis

**Completion criteria.** Over-admission test passes against two instances.

**Interview checkpoints.** Shared Redis; two JVMs; hot keys; measured k6 numbers.

---

## Milestone 11: TLS and AWS EC2 deployment

**Objective.** HTTPS demo on a single EC2 host.

**Dependencies.** Milestone 10 for a **distributed** deployment claim.

**Implementation tasks**

- [ ] nginx TLS (Let’s Encrypt or self-signed)
- [ ] Security group: 80/443 public; SSH from your IP; no Redis/Postgres
- [ ] Host `.env` (`chmod 600`); JVM memory caps

**Required tests**

- [ ] Manual HTTPS; Actuator/internal not public

**Completion criteria.** Reachable HTTPS demo; secrets not in Git.

**Interview checkpoints.** TLS termination; security groups.

---

## Milestone 12: Optional advanced features

**Objective.** At most one or two extras after Milestone 10.

**Dependencies.** Milestone 10.

**Implementation tasks (pick later; all deferred)**

- [ ] Redis Pub/Sub + continued polling
- [ ] Resilience4j circuit breaker
- [ ] Metrics `batchId` deduplication
- [ ] Sliding Window Counter
- [ ] Per-client overrides / multi-limit / token cost
- [ ] IP anonymous limit
- [ ] `admin_users` + RBAC
- [ ] Prometheus / Grafana
- [ ] Policy audit table
- [ ] Compare built-in `RedisRateLimiter`
- [ ] On-demand API-key cache

**Required tests.** Feature-specific; add when a item is chosen.

**Completion criteria.** Only if you explicitly select an extra.

**Interview checkpoints.** Depend on the chosen extra.

---

## Out of scope (frozen)

Eureka, Spring Cloud Config Server, Kafka, Kubernetes, Redis Cluster, a separate Rate Limiter microservice.
