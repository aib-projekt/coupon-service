# Design Decisions — Coupon Service

*Converged during Phase 5. Full alternatives in: analysis/alternatives.md*

## Selected Approach

**Spring Boot 3 + WebFlux reactive backend with PostgreSQL via R2DBC, atomic SQL concurrency, ip-api.com geolocation, resource-oriented REST API, and Flyway migrations.**

---

## Decisions by Area

### 1. Framework: Spring Boot 3 + Spring WebFlux

**Chosen**: Spring Boot 3 with Spring WebFlux (reactive, non-blocking)

**Rationale**: Reactive architecture with Project Reactor (Mono/Flux) throughout. Non-blocking I/O enables the geolocation HTTP call and database access to be fully async. All endpoints return reactive types.

**Trade-offs accepted**:
- Higher implementation complexity than Spring MVC
- R2DBC is less feature-rich than JPA (no JPQL, no dirty checking)
- Reactive transaction management requires context propagation

**Alternatives rejected**: Spring MVC (blocking), Quarkus

---

### 2. Database & ORM: PostgreSQL + Spring Data R2DBC

**Chosen**: PostgreSQL as the database; Spring Data R2DBC for repository layer; `DatabaseClient` for the atomic counter UPDATE

**Rationale**: R2DBC is the only fully non-blocking JDBC alternative compatible with WebFlux. Spring Data R2DBC provides `R2dbcRepository` for standard CRUD and `DatabaseClient` / `R2dbcEntityTemplate` for custom native queries.

**Trade-offs accepted**:
- No JPQL, no Hibernate magic; queries must be explicit
- Reactive `@Transactional` with context propagation instead of thread-local

**Alternatives rejected**: JPA/Hibernate (blocking), H2 in production (not shared, not representative)

**Testing**: Testcontainers with PostgreSQL for integration tests against a real database

---

### 3. Concurrency: Atomic SQL UPDATE (row-count check)

**Chosen**: Single `UPDATE coupons SET current_uses = current_uses + 1 WHERE code = :code AND current_uses < max_uses` via `DatabaseClient`. Check affected row count.

```sql
UPDATE coupons
SET current_uses = current_uses + 1
WHERE code = :code
  AND current_uses < max_uses
```

If affected rows = 0 → either coupon not found OR cap already reached (differentiate by a preceding SELECT).

**Rationale**: Single round-trip, PostgreSQL-atomic, no retry loops, no version columns. Perfectly compatible with R2DBC's `DatabaseClient` reactive API.

**Redeem operation order** (critical for correctness):
1. Look up coupon (SELECT) → check existence + country
2. Geolocation check (external HTTP call, outside transaction)
3. Per-user check (SELECT from coupon_usages, if perUserLimit=true)
4. Atomic UPDATE (increment with cap condition)
5. Insert into coupon_usages (if perUserLimit=true)

Steps 4+5 run in a reactive transaction.

**Trade-offs accepted**:
- Custom SQL required for the counter update; no ORM-generated query
- Redeem flow has strict ordering requirements

**Alternatives rejected**: Optimistic locking (retry complexity), Pessimistic locking (lock held across geo I/O)

---

### 4. Geolocation: ip-api.com via GeoLocationService interface

**Chosen**: ip-api.com free HTTP API; provider hidden behind a `GeoLocationService` interface

**Interface**:
```java
interface GeoLocationService {
    Mono<String> getCountryCode(String ipAddress);  // returns ISO 3166-1 alpha-2 or error
}
```

**Implementation**: `WebClient` (reactive HTTP client) calling `http://ip-api.com/json/{ip}?fields=status,countryCode`

**Failure handling**: Any network error, timeout, or non-OK status → propagate as `GEO_UNAVAILABLE` error (fail closed)

**IP source**: Read from `X-Forwarded-For` request header (first IP in the comma-separated list)

**Rationale**: Zero setup for a recruitment exercise. Interface abstraction allows swapping to GeoLite2 or ipapi.co without touching business logic.

**Trade-offs accepted**:
- HTTP only (not HTTPS) on free tier
- 45 req/min rate limit — acceptable for recruitment context
- External service failure = redemption failure (fail closed by design)

**Alternatives rejected**: ipapi.co (minor advantage), GeoLite2 (registration + .mmdb bundle needed)

---

### 5. API Design: Resource-oriented REST with /redeem action

**Chosen**:
```
POST   /api/v1/coupons                  → 201 Created
GET    /api/v1/coupons/{code}           → 200 OK
POST   /api/v1/coupons/{code}/redeem    → 200 OK
```

**Request/Response contracts** (summary — full detail in feature spec):
- `POST /coupons` body: `{code, maxUses, country, perUserLimit}`
- `GET /coupons/{code}` response: `{code, country, maxUses, currentUses, perUserLimit, createdAt}`
- `POST /coupons/{code}/redeem` body: `{userId}` (required only if perUserLimit=true)
- Errors: `{"error": "ERROR_CODE", "message": "Human readable message"}`

**Error codes**:
- `COUPON_NOT_FOUND` (404)
- `COUPON_EXHAUSTED` (409)
- `COUNTRY_NOT_ALLOWED` (403)
- `ALREADY_USED` (409)
- `GEO_UNAVAILABLE` (503)
- `COUPON_ALREADY_EXISTS` (409)
- `INVALID_REQUEST` (400)

**Trade-offs accepted**:
- `/redeem` is a verb in the URL (pragmatic compromise over strict REST)

**Alternatives rejected**: Command-oriented (not RESTful), Strict REST with redemption sub-resource (adds unrequired complexity)

---

### 6. Migrations: Flyway

**Chosen**: Flyway with SQL migration files in `src/main/resources/db/migration/`

**Naming convention**: `V{n}__{description}.sql`
- `V1__create_coupons_table.sql`
- `V2__create_coupon_usages_table.sql`

**Rationale**: Simplest migration tool, SQL-first, Spring Boot autoconfigured, widely recognized.

**Trade-offs accepted**: No built-in rollback (acceptable for this bounded schema with 2 tables)

**Alternatives rejected**: Liquibase (verbose, rollback not needed), Manual DDL (not production-grade)

---

## Key Architectural Invariants

1. **Service instances are stateless** — all shared state lives in PostgreSQL
2. **IP source is always X-Forwarded-For** — the first non-private IP in the header chain
3. **Coupon codes are normalized to uppercase** at the API boundary (controller layer) before any lookup or storage
4. **Geolocation is always called before the atomic UPDATE** — avoid incrementing the counter if geo fails
5. **GeoLocationService is an interface** — the implementation is swappable; business logic has no direct dependency on ip-api.com
6. **perUserLimit is a coupon-level flag** — when false, no userId is required and coupon_usages is not written
