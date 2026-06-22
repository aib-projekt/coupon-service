# Coupon Service

REST API for discount coupon lifecycle management — creation, retrieval, and atomic redemption with IP-based country restriction.
Implementation of a recruitment task whose requirements are part of the maister's analysis: [coupon-service.md](.maister/tasks/product-design/2026-05-08-coupon-service/context/coupon-servcie.md)

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 25 |
| Framework | Spring Boot 3.3.11 + WebFlux (reactive, non-blocking) |
| Database | PostgreSQL 18 + Spring Data R2DBC |
| Migrations | Flyway (JDBC driver alongside R2DBC) |
| Build | Apache Maven 3.8+ |
| Geolocation | [ip-api.com](http://ip-api.com) — no API key required |
| API docs | Springdoc OpenAPI (Swagger UI at `/swagger-ui.html`) |

## Running Locally

### Prerequisites

- Docker + Docker Compose
- Java 25 JDK (for building)
- Maven 3.8+

### Build & start

```bash
# 1. Build the JAR
mvn clean package -DskipTests

# 2. Copy and configure environment variables
cd local-docker
cp .env.example .env
# Fill in DATABASE_USERNAME and DATABASE_PASSWORD in .env

# 3. Start the stack — Postgres + nginx + 4 app instances
docker compose build app
docker compose up -d --scale app=4 --wait --wait-timeout 60

# 4. Verify health
docker compose ps   # all services should show "healthy"
```

The API is available at `http://localhost:8080`.

### Quick smoke test

Run the bundled check script from the `local-docker/` directory:

```bash
cd local-docker
bash local-check.sh
```

The script covers the main scenarios end-to-end:

| Scenario | What it verifies |
|---|---|
| Create 10 coupons | Basic creation, response shape |
| 15 parallel redeems on `LIMIT10` (maxUses=10) | Atomicity — `currentUses` must equal `10`, never more |
| Same `userId` redeems `PERUSER` twice | Second attempt returns `409 ALREADY_USED` |
| Different `userId` redeems `PERUSER` | Returns `200 OK` |
| 40 parallel redeems across 4 instances (nginx) | Multi-instance atomicity — `currentUses` must equal `10`, never more |

Add `set -x` at the top of the script to print each executed command alongside its output.

### Scale to multiple instances

```bash
docker compose up -d --scale app=4   # nginx load balances across 4 instances
```

### Reset the database

```bash
docker compose down -v   # stops containers and removes the postgres-data volume
```

## API Reference

### Create coupon

```
POST /api/v1/coupons
Content-Type: application/json

{
  "code": "SPRING20",
  "maxUses": 100,
  "country": "PL",
  "perUserLimit": false
}
```

| Field | Type | Description |
|---|---|---|
| `code` | string | Alphanumeric, max 64 chars. Stored uppercase, lookup is case-insensitive |
| `maxUses` | integer | ≥ 1. Hard cap on total redemptions |
| `country` | string | ISO 3166-1 alpha-2 (e.g. `PL`, `DE`, `US`) |
| `perUserLimit` | boolean | If `true`, each `userId` may redeem this coupon only once |

Responses: `201 Created` · `400 Bad Request` (validation) · `409 Conflict` (duplicate code)

---

### Get coupon

```
GET /api/v1/coupons/{code}
```

Returns current state including `currentUses`. Lookup is case-insensitive.

Responses: `200 OK` · `404 Not Found`

---

### Redeem coupon

```
POST /api/v1/coupons/{code}/redeem
Content-Type: application/json
X-Forwarded-For: <client-ip>

{
  "userId": "user-123"   // required only when perUserLimit=true
}
```

The service resolves the caller's country from the IP in `X-Forwarded-For`. Redemption is **atomic** — the usage counter is incremented and validated in a single SQL statement, guaranteeing `currentUses` never exceeds `maxUses` under concurrent load.

| HTTP Status | Error code | Meaning |
|---|---|---|
| `200 OK` | — | Coupon redeemed; response includes `remainingUses` |
| `403 Forbidden` | `COUNTRY_NOT_ALLOWED` | Caller's IP resolves to a different country |
| `404 Not Found` | `COUPON_NOT_FOUND` | Code does not exist |
| `409 Conflict` | `COUPON_EXHAUSTED` | `maxUses` already reached |
| `409 Conflict` | `ALREADY_USED` | This `userId` has already redeemed this coupon |
| `503 Service Unavailable` | `GEO_UNAVAILABLE` | Geolocation service unreachable (fail closed) |

## Architecture

```
HTTP Request
    │
    ▼
CouponController          (WebFlux @RestController, input validation)
    │
    ▼
CouponService             (business rules, reactive pipeline)
    │
    ├──► GeoLocationService  (interface → IpApiGeoLocationService)
    │         resolves country from IP; fail closed on timeout/error
    │
    └──► CouponRepository    (Spring Data R2DBC + custom atomic query)
              atomic UPDATE: current_uses = current_uses + 1
              WHERE current_uses < max_uses
              RETURNING max_uses - current_uses
```

### Key design decisions

**Atomic concurrency** — redemption uses a single `UPDATE ... WHERE current_uses < max_uses RETURNING ...` statement. No optimistic locking, no distributed locks. The database guarantees the cap is never exceeded regardless of how many instances run simultaneously.

**Geolocation fail closed** — if ip-api.com is unreachable (timeout or error), the redemption is rejected with `503 GEO_UNAVAILABLE`. Coupons are never redeemed when the country cannot be verified.

**Per-user enforcement via database constraint** — the `UNIQUE(coupon_code, user_id)` constraint on `coupon_usages` is the authoritative guard against double redemption. No pre-check SELECT is performed; a constraint violation is caught and mapped to `ALREADY_USED`.

**Trusted proxy hops** — `X-Forwarded-For` parsing respects the `geo.trusted-proxy-hops` configuration (default: `1`). Set it to the number of load balancers in front of the service to prevent IP spoofing.

**Case-insensitive codes** — coupon codes are normalized to uppercase at the API boundary. `SPRING20`, `spring20`, and `Spring20` all refer to the same coupon.

## Configuration

All configuration is provided via environment variables (see `local-docker/.env.example`):

| Variable | Description |
|---|---|
| `SPRING_R2DBC_URL` | R2DBC connection URL, e.g. `r2dbc:postgresql://host:5432/db` |
| `SPRING_FLYWAY_URL` | JDBC connection URL for Flyway, e.g. `jdbc:postgresql://host:5432/db` |
| `SPRING_R2DBC_USERNAME` | Database username |
| `SPRING_R2DBC_PASSWORD` | Database password |
| `GEO_BASE_URL` | Geolocation API base URL (default: `http://ip-api.com`) |
| `GEO_CONNECT_TIMEOUT` | TCP connect timeout in seconds (default: `2`) |
| `GEO_READ_TIMEOUT` | Response read timeout in seconds (default: `3`) |
| `GEO_TRUSTED_PROXY_HOPS` | Number of trusted reverse proxies (default: `1`) |

## Database Schema

```sql
-- Coupons
CREATE TABLE coupons (
    code           VARCHAR(64)  PRIMARY KEY,
    max_uses       INTEGER      NOT NULL CHECK (max_uses >= 1),
    current_uses   INTEGER      NOT NULL DEFAULT 0 CHECK (current_uses >= 0),
    country        CHAR(2)      NOT NULL,
    per_user_limit BOOLEAN      NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Per-user redemption tracking (populated only when perUserLimit=true)
CREATE TABLE coupon_usages (
    id           BIGSERIAL    PRIMARY KEY,
    coupon_code  VARCHAR(64)  NOT NULL REFERENCES coupons(code),
    user_id      VARCHAR(255) NOT NULL,
    used_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_coupon_usages_per_user UNIQUE (coupon_code, user_id)
);
```

## Tests

```bash
# Unit tests only
mvn test

# Unit + integration tests (requires Docker for Testcontainers)
mvn verify
```

Integration tests (`*IT.java`) use Testcontainers to spin up a real PostgreSQL instance. JaCoCo enforces ≥ 80% instruction and branch coverage — the build fails if the gate is not met.
