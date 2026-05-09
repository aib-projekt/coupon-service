# Feature Specification — Coupon Service REST API

*Approved section-by-section during Phase 6*
*Framework: Spring Boot 3 + WebFlux | DB: PostgreSQL + R2DBC | Migrations: Flyway*

---

## Section 1: Data Model

### Domain Entities

#### Coupon

| Field | Type | Constraints |
|---|---|---|
| `code` | `VARCHAR(64)` | PRIMARY KEY, stored uppercase, alphanumeric [A-Z0-9] |
| `max_uses` | `INTEGER` | NOT NULL, CHECK >= 1 |
| `current_uses` | `INTEGER` | NOT NULL DEFAULT 0, CHECK >= 0, always <= max_uses |
| `country` | `CHAR(2)` | NOT NULL, ISO 3166-1 alpha-2 |
| `per_user_limit` | `BOOLEAN` | NOT NULL DEFAULT false |
| `created_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT now() |

#### CouponUsage *(written only when perUserLimit = true)*

| Field | Type | Constraints |
|---|---|---|
| `id` | `BIGSERIAL` | PRIMARY KEY |
| `coupon_code` | `VARCHAR(64)` | FK → coupons.code, NOT NULL |
| `user_id` | `VARCHAR(255)` | NOT NULL |
| `used_at` | `TIMESTAMPTZ` | NOT NULL DEFAULT now() |
| — | — | UNIQUE(coupon_code, user_id) |

### Database Invariants

- `current_uses` never exceeds `max_uses` — enforced by the atomic UPDATE condition
- Coupon codes are stored uppercase and the PK uniqueness enforces code uniqueness case-insensitively (since all codes are normalized before storage)
- `UNIQUE(coupon_code, user_id)` on `coupon_usages` prevents double-use at the database level, complementing the application-level check

### Flyway Migration Files

**`V1__create_coupons_table.sql`**
```sql
CREATE TABLE coupons (
    code           VARCHAR(64)  PRIMARY KEY,
    max_uses       INTEGER      NOT NULL CHECK (max_uses >= 1),
    current_uses   INTEGER      NOT NULL DEFAULT 0 CHECK (current_uses >= 0),
    country        CHAR(2)      NOT NULL,
    per_user_limit BOOLEAN      NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

**`V2__create_coupon_usages_table.sql`**
```sql
CREATE TABLE coupon_usages (
    id           BIGSERIAL    PRIMARY KEY,
    coupon_code  VARCHAR(64)  NOT NULL REFERENCES coupons(code),
    user_id      VARCHAR(255) NOT NULL,
    used_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (coupon_code, user_id)
);
```

### Java Entities (Spring Data R2DBC)

```java
@Table("coupons")
public record Coupon(
    @Id String code,
    int maxUses,
    int currentUses,
    String country,
    boolean perUserLimit,
    Instant createdAt
) {}

@Table("coupon_usages")
public record CouponUsage(
    @Id Long id,
    String couponCode,
    String userId,
    Instant usedAt
) {}
```

### Repository Interfaces

```java
@Repository
public interface CouponRepository extends R2dbcRepository<Coupon, String> {
    // Basic CRUD via R2dbcRepository
    // Atomic counter update via CouponRepositoryCustom
}

@Repository
public interface CouponUsageRepository extends R2dbcRepository<CouponUsage, Long> {
    Mono<Boolean> existsByCouponCodeAndUserId(String couponCode, String userId);
    Mono<CouponUsage> findByCouponCodeAndUserId(String couponCode, String userId);
}
```

Atomic increment method lives in a custom repository implementation using `DatabaseClient`:

```java
// Returns Mono<Integer> — number of updated rows (1 = success, 0 = cap reached or not found)
Mono<Integer> atomicIncrementUsage(String couponCode);
```

```sql
-- Underlying SQL
UPDATE coupons
SET current_uses = current_uses + 1
WHERE code = :code
  AND current_uses < max_uses
```

---

## Section 2: API Contract

### Endpoints

#### `POST /api/v1/coupons` — Create Coupon

**Request**
```http
POST /api/v1/coupons
Content-Type: application/json

{
  "code": "SPRING2026",
  "maxUses": 100,
  "country": "PL",
  "perUserLimit": false
}
```

| Field | Type | Required | Validation |
|---|---|---|---|
| `code` | `String` | Yes | Alphanumeric [A-Za-z0-9], 1–64 chars |
| `maxUses` | `int` | Yes | >= 1 |
| `country` | `String` | Yes | Valid ISO 3166-1 alpha-2 code |
| `perUserLimit` | `boolean` | No | Default: `false` |

**Responses**

`201 Created`
```json
{
  "code": "SPRING2026",
  "maxUses": 100,
  "currentUses": 0,
  "country": "PL",
  "perUserLimit": false,
  "createdAt": "2026-05-08T10:00:00Z"
}
```

`409 Conflict` — code already exists
```json
{"error": "COUPON_ALREADY_EXISTS", "message": "Coupon with code 'SPRING2026' already exists."}
```

`400 Bad Request` — validation failure
```json
{"error": "INVALID_REQUEST", "message": "Field 'maxUses' must be >= 1."}
```

---

#### `GET /api/v1/coupons/{code}` — Get Coupon

Path parameter `code` is normalized to uppercase before lookup.

**Response `200 OK`**
```json
{
  "code": "SPRING2026",
  "maxUses": 100,
  "currentUses": 47,
  "country": "PL",
  "perUserLimit": false,
  "createdAt": "2026-05-08T10:00:00Z"
}
```

**Response `404 Not Found`**
```json
{"error": "COUPON_NOT_FOUND", "message": "Coupon 'SPRING2026' not found."}
```

---

#### `POST /api/v1/coupons/{code}/redeem` — Redeem Coupon

**Request**
```http
POST /api/v1/coupons/SPRING2026/redeem
Content-Type: application/json
X-Forwarded-For: 203.0.113.45

{
  "userId": "user-abc-123"
}
```

`userId` is required in the body if and only if `perUserLimit = true` on the coupon. If `perUserLimit = false`, the body may be empty.

`X-Forwarded-For` header: service reads the first non-private IP in the header chain for geolocation.

**Response `200 OK`**
```json
{
  "code": "SPRING2026",
  "remainingUses": 52,
  "redeemedAt": "2026-05-08T14:30:00Z"
}
```

**Error responses**

| HTTP | Error code | Condition |
|---|---|---|
| 400 | `INVALID_REQUEST` | Missing `userId` when `perUserLimit=true`; malformed request |
| 403 | `COUNTRY_NOT_ALLOWED` | User IP resolves to a country different from coupon's country |
| 404 | `COUPON_NOT_FOUND` | Coupon code does not exist |
| 409 | `COUPON_EXHAUSTED` | `current_uses >= max_uses` at time of atomic update |
| 409 | `ALREADY_USED` | `coupon_usages` row exists for (couponCode, userId) |
| 503 | `GEO_UNAVAILABLE` | ip-api.com unreachable or returns error status |

### HTTP Status Code Summary

| Status | Meaning |
|---|---|
| 200 | Success (GET coupon, redeem) |
| 201 | Created (coupon creation) |
| 400 | Bad Request (validation) |
| 403 | Forbidden (wrong country) |
| 404 | Not Found |
| 409 | Conflict (duplicate code, exhausted, already used) |
| 503 | Service Unavailable (geo service down) |

---

## Section 3: Redemption Flow & Business Logic

### Redeem Operation — Ordered Steps

> **Critical**: The ordering of steps below is mandatory. Steps 1–5 execute outside the database transaction to minimize lock window and keep the geo HTTP call out of the transaction.

**Input**: `code` (path), `userId` (body, optional), `X-Forwarded-For` (header)

| Step | Action | Failure condition | Error response |
|---|---|---|---|
| 1 | Normalize `code` to uppercase | — | — |
| 2 | Extract client IP from `X-Forwarded-For` (first non-private IP) | Header missing/empty → use connecting IP | — |
| 3 | `SELECT * FROM coupons WHERE code = :code` | Row not found | `COUPON_NOT_FOUND` 404 |
| 4 | Call GeoLocationService with client IP | Network error / timeout / non-OK status | `GEO_UNAVAILABLE` 503 |
| 4b | Compare returned `countryCode` with `coupon.country` | Mismatch | `COUNTRY_NOT_ALLOWED` 403 |
| 5 | If `perUserLimit = false` → skip to Step 6 | — | — |
| 5a | Validate `userId` present in request body | Missing | `INVALID_REQUEST` 400 |
| 5b | `SELECT 1 FROM coupon_usages WHERE coupon_code = :code AND user_id = :userId` | Row exists | `ALREADY_USED` 409 |
| 6 | **BEGIN TRANSACTION** | — | — |
| 6a | `UPDATE coupons SET current_uses = current_uses + 1 WHERE code = :code AND current_uses < max_uses` | Affected rows = 0 | `COUPON_EXHAUSTED` 409 + ROLLBACK |
| 6b | If `perUserLimit = true`: `INSERT INTO coupon_usages (coupon_code, user_id, used_at) VALUES (:code, :userId, now())` | UNIQUE constraint violation | `ALREADY_USED` 409 + ROLLBACK |
| 6c | **COMMIT** | — | — |
| 7 | Return `200 OK` with `remainingUses = maxUses - currentUses` (post-update) | — | — |

### Race Condition Analysis

**Race 1: Two requests racing for the last use slot**

Both requests pass Step 3 (SELECT) and Step 4 (geo check) concurrently. Both reach Step 6a with `current_uses = max_uses - 1`.

→ Only one `UPDATE` succeeds (PostgreSQL atomic: the second finds `current_uses = max_uses`).
→ First request: affected rows = 1 → continues → COMMIT → 200 OK.
→ Second request: affected rows = 0 → ROLLBACK → `COUPON_EXHAUSTED` 409.

**Race 2: Two requests for the same (coupon, user) pair**

Both pass Step 5b (SELECT from coupon_usages, neither row exists yet). Both reach Step 6b.

→ Both INSERT succeed up to the UNIQUE constraint.
→ Second INSERT violates `UNIQUE(coupon_code, user_id)` → DB raises constraint violation.
→ Application catches `R2dbcDataIntegrityViolationException` → maps to `ALREADY_USED` 409 → ROLLBACK.

The application-level check in Step 5b is an optimization (fast path); the DB constraint is the safety net.

---

## Section 4: Service Layer Architecture

### Package Structure

```
pl.aibprojekt.couponservice/
├── api/
│   ├── CouponController.java              // WebFlux @RestController
│   └── dto/
│       ├── CreateCouponRequest.java       // @Valid request DTO
│       ├── RedeemCouponRequest.java
│       ├── CouponResponse.java
│       ├── RedemptionResponse.java
│       └── ErrorResponse.java             // {error, message}
│   └── validation/
│       └── CountryCodeValidator.java      // @ValidCountryCode annotation
├── application/
│   ├── CouponService.java                 // Business logic, orchestrates flow
│   └── GeoLocationService.java            // Interface (port)
├── domain/
│   ├── Coupon.java                        // R2DBC @Table record
│   ├── CouponUsage.java
│   ├── CouponErrorCode.java               // Enum: COUPON_NOT_FOUND, COUPON_EXHAUSTED, ...
│   └── CouponException.java               // RuntimeException with CouponErrorCode + HTTP status
└── infrastructure/
    ├── geo/
    │   └── IpApiGeoLocationService.java   // WebClient-based implementation
    ├── persistence/
    │   ├── CouponRepository.java          // R2dbcRepository<Coupon, String>
    │   ├── CouponUsageRepository.java
    │   └── CouponRepositoryCustomImpl.java // atomicIncrementUsage()
    └── config/
        ├── WebClientConfig.java            // WebClient bean for ip-api.com
        └── GlobalExceptionHandler.java     // @ControllerAdvice
```

### Layer Dependencies

```
api → application (CouponService) → domain
infrastructure → application (implements GeoLocationService interface)
infrastructure.persistence → domain (Coupon, CouponUsage records)
api → dto only — never returns domain records directly
```

No upward references: domain has zero dependencies on infrastructure or API.

### Key Interfaces

**GeoLocationService** (in `application/`):
```java
public interface GeoLocationService {
    Mono<String> getCountryCode(String ipAddress);
    // Returns ISO 3166-1 alpha-2 country code
    // Signals failure via Mono.error(new CouponException(GEO_UNAVAILABLE))
}
```

**CouponService** (in `application/`):
```java
public interface CouponService {
    Mono<CouponResponse> createCoupon(CreateCouponRequest request);
    Mono<CouponResponse> getCoupon(String code);
    Mono<RedemptionResponse> redeemCoupon(String code, String clientIp, RedeemCouponRequest request);
}
```

**CouponRepositoryCustom** (in `infrastructure/persistence/`):
```java
public interface CouponRepositoryCustom {
    Mono<Integer> atomicIncrementUsage(String couponCode);
    // Returns 1 if updated, 0 if cap reached or not found
}
```

### Exception Model

```java
public enum CouponErrorCode {
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND),
    COUPON_EXHAUSTED(HttpStatus.CONFLICT),
    COUNTRY_NOT_ALLOWED(HttpStatus.FORBIDDEN),
    ALREADY_USED(HttpStatus.CONFLICT),
    GEO_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    COUPON_ALREADY_EXISTS(HttpStatus.CONFLICT),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST);

    final HttpStatus httpStatus;
}

public class CouponException extends RuntimeException {
    private final CouponErrorCode errorCode;
    // Constructor: CouponException(CouponErrorCode errorCode, String message)
}
```

**GlobalExceptionHandler** (`@ControllerAdvice`):

| Exception | Maps to |
|---|---|
| `CouponException` | `{error: errorCode.name(), message: ex.getMessage()}` with matching HTTP status |
| `WebExchangeBindException` (Bean Validation) | 400 `INVALID_REQUEST` |
| `DataIntegrityViolationException` (UNIQUE coupon_code) | 409 `COUPON_ALREADY_EXISTS` |
| `DataIntegrityViolationException` (UNIQUE coupon_code+user_id) | 409 `ALREADY_USED` |
| `Exception` (catch-all) | 500, no internal details leaked |

### Reactive Transaction Scope

`@Transactional` on `CouponServiceImpl.redeemCoupon()` covers only the database write operations in Step 6 (atomic UPDATE + optional INSERT into coupon_usages). Steps 1–5 (SELECT coupon, geo HTTP call, per-user SELECT) run before the transaction begins to keep the transaction window minimal.

### Coupon Creation Business Rules

1. `code` is normalized to uppercase before INSERT — storage is always uppercase
2. `code` must match `[A-Za-z0-9]{1,64}` — validated at controller layer before any DB access
3. `country` must be a recognized ISO 3166-1 alpha-2 code — validate against a complete list
4. `maxUses` must be >= 1
5. If `code` already exists (PK violation) → 409 `COUPON_ALREADY_EXISTS`

### Input Validation Rules

| Field | Rule | Error |
|---|---|---|
| `code` (create) | `[A-Za-z0-9]{1,64}` | `INVALID_REQUEST` |
| `maxUses` | integer, >= 1 | `INVALID_REQUEST` |
| `country` | valid ISO 3166-1 alpha-2 | `INVALID_REQUEST` |
| `userId` (redeem) | non-blank string, required if `perUserLimit=true` | `INVALID_REQUEST` |
| `code` (redeem/get) | any string accepted; normalized to uppercase before lookup | — |

---

## Section 5: Geolocation Integration

### IP Address Extraction

Source: `X-Forwarded-For` request header (set by the e-commerce backend for all server-to-server calls).

```
Example header: X-Forwarded-For: 203.0.113.45, 10.0.0.1, 172.16.0.1
Rule: use the first non-private IP address (leftmost = original client IP)
```

Private IP ranges to skip (RFC 1918 + loopback):
- `10.0.0.0/8`
- `172.16.0.0/12`
- `192.168.0.0/16`
- `127.0.0.0/8`
- `::1` (IPv6 loopback)

Fallback: if header is absent or all IPs are private → use the request's remote address.

### ip-api.com Request

```
GET http://ip-api.com/json/{ip}?fields=status,countryCode
```

| Config | Value | Override via |
|---|---|---|
| Base URL | `http://ip-api.com` | `geo.base-url` in `application.yml` |
| Connect timeout | 2 seconds | `geo.connect-timeout-seconds` |
| Read timeout | 3 seconds | `geo.read-timeout-seconds` |

### Success Response Parsing

```json
{"status": "success", "countryCode": "PL"}
```

- `status == "success"` AND `countryCode` non-blank → return `countryCode`
- `status == "fail"` (private IP, reserved range, etc.) → propagate as `GEO_UNAVAILABLE`

### Failure Cases → All Map to GEO_UNAVAILABLE (503)

| Condition | Handling |
|---|---|
| Network timeout | `Mono.error(new CouponException(GEO_UNAVAILABLE, ...))` |
| HTTP non-2xx response | Same |
| `status = "fail"` in JSON body | Same |
| `countryCode` blank/null | Same |

### Configuration (`application.yml`)

```yaml
geo:
  base-url: http://ip-api.com
  connect-timeout-seconds: 2
  read-timeout-seconds: 3
```

### Interface Swappability

`IpApiGeoLocationService` is the only class that knows about ip-api.com. It is registered via a `@Configuration` class:

```java
@Configuration
public class GeoLocationConfig {
    @Bean
    public GeoLocationService geoLocationService(WebClient.Builder builder, GeoProperties props) {
        return new IpApiGeoLocationService(builder, props);
    }
}
```

To swap providers (e.g., to GeoLite2): replace the `@Bean` definition, no business logic changes.

---

## Section 6: Testing Strategy

### Test Layers

#### Unit Tests (JUnit 5 + Mockito — no Spring context)

Target: `CouponServiceImpl`, `IpApiGeoLocationService`, `CountryCodeValidator`, IP extraction utility

`CouponServiceImpl` test cases (all business logic branches):
- `should_return_COUPON_NOT_FOUND_when_code_does_not_exist()`
- `should_return_GEO_UNAVAILABLE_when_geolocation_service_fails()`
- `should_return_COUNTRY_NOT_ALLOWED_when_user_ip_is_from_wrong_country()`
- `should_return_INVALID_REQUEST_when_perUserLimit_true_and_userId_missing()`
- `should_return_ALREADY_USED_when_user_has_already_redeemed_coupon()`
- `should_return_COUPON_EXHAUSTED_when_atomic_update_affects_zero_rows()`
- `should_return_RedemptionResponse_on_successful_redemption()`
- `should_return_RedemptionResponse_when_perUserLimit_false_and_userId_not_provided()`

`IpApiGeoLocationService` test cases (mock WebClient):
- `should_return_country_code_on_success_response()`
- `should_signal_GEO_UNAVAILABLE_on_network_timeout()`
- `should_signal_GEO_UNAVAILABLE_when_status_is_fail()`
- `should_signal_GEO_UNAVAILABLE_on_non_2xx_http_response()`

#### Integration Tests (`@SpringBootTest` + Testcontainers PostgreSQL)

`GeoLocationService` is mocked in all integration tests — no external HTTP calls in CI.

**`POST /api/v1/coupons`**
- `should_create_coupon_and_return_201()`
- `should_return_409_when_coupon_code_already_exists()`
- `should_return_400_when_maxUses_is_zero()`
- `should_return_400_when_country_is_invalid_iso_code()`
- `should_normalize_code_to_uppercase_on_create()`

**`GET /api/v1/coupons/{code}`**
- `should_return_coupon_details_with_200()`
- `should_return_404_when_coupon_not_found()`
- `should_find_coupon_regardless_of_input_case()`

**`POST /api/v1/coupons/{code}/redeem`**
- `should_redeem_coupon_and_return_200_with_remainingUses()`
- `should_return_404_for_nonexistent_coupon()`
- `should_return_403_when_country_does_not_match()`
- `should_return_503_when_geolocation_service_unavailable()`
- `should_return_409_COUPON_EXHAUSTED_when_max_uses_reached()`
- `should_return_409_ALREADY_USED_when_user_redeems_twice_with_perUserLimit_true()`
- `should_allow_multiple_redemptions_by_same_user_when_perUserLimit_false()`
- `should_return_400_when_userId_missing_and_perUserLimit_true()`

**Concurrency tests**
- `should_not_exceed_maxUses_under_concurrent_redemptions()` — launch N threads simultaneously; assert exactly `maxUses` succeed and the rest get `COUPON_EXHAUSTED`
- `should_return_ALREADY_USED_on_concurrent_per_user_duplicate()` — two threads race with same userId; exactly one succeeds

### Mock Strategy

| Dependency | Unit tests | Integration tests |
|---|---|---|
| `GeoLocationService` | Mocked (Mockito) | Mocked (`@MockBean`) |
| PostgreSQL | N/A | Real (Testcontainers) |
| `WebClient` | Mocked (Mockito) | N/A (geo is mocked) |

Never use H2 for tests — all integration tests run against a real PostgreSQL instance via Testcontainers.

---

## Section 7: Infrastructure & Configuration

### Maven Dependencies (`pom.xml` additions)

```xml
<!-- Web -->
<dependency>spring-boot-starter-webflux</dependency>

<!-- Database (reactive) -->
<dependency>spring-boot-starter-data-r2dbc</dependency>
<dependency>io.r2dbc:r2dbc-postgresql</dependency>
<dependency>org.postgresql:postgresql</dependency>  <!-- Required by Flyway (JDBC) -->

<!-- Migrations -->
<dependency>org.flywaydb:flyway-core</dependency>
<dependency>org.flywaydb:flyway-database-postgresql</dependency>

<!-- Validation -->
<dependency>spring-boot-starter-validation</dependency>

<!-- Observability -->
<dependency>spring-boot-starter-actuator</dependency>

<!-- Test -->
<dependency>org.testcontainers:postgresql (test)</dependency>
<dependency>org.testcontainers:junit-jupiter (test)</dependency>
<dependency>io.projectreactor:reactor-test (test)</dependency>

<!-- Coverage -->
<plugin>org.jacoco:jacoco-maven-plugin</plugin>
```

### Docker Setup

**`docker-compose.yml`** (local development)
```yaml
services:
  postgres:
    image: postgres:18
    environment:
      POSTGRES_DB: couponservice
      POSTGRES_USER: coupon
      POSTGRES_PASSWORD: coupon
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U coupon"]
      interval: 5s
      timeout: 5s
      retries: 5

  app:
    image: coupon-service:latest
    build: .
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      SPRING_R2DBC_URL: r2dbc:postgresql://postgres:5432/couponservice
      SPRING_R2DBC_USERNAME: coupon
      SPRING_R2DBC_PASSWORD: coupon
      SPRING_FLYWAY_URL: jdbc:postgresql://postgres:5432/couponservice
    ports:
      - "8080:8080"
```

**`Dockerfile`**
```dockerfile
FROM eclipse-temurin:25-jre
COPY target/*.jar /app/app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

### Application Configuration (`application.yml`)

```yaml
spring:
  r2dbc:
    url: ${SPRING_R2DBC_URL:r2dbc:postgresql://localhost:5432/couponservice}
    username: ${SPRING_R2DBC_USERNAME:coupon}
    password: ${SPRING_R2DBC_PASSWORD:coupon}
  flyway:
    url: ${SPRING_FLYWAY_URL:jdbc:postgresql://localhost:5432/couponservice}
    username: ${SPRING_R2DBC_USERNAME:coupon}
    password: ${SPRING_R2DBC_PASSWORD:coupon}
    locations: classpath:db/migration

geo:
  base-url: ${GEO_BASE_URL:http://ip-api.com}
  connect-timeout-seconds: ${GEO_CONNECT_TIMEOUT:2}
  read-timeout-seconds: ${GEO_READ_TIMEOUT:3}

management:
  endpoints:
    web:
      exposure:
        include: health
```

### Flyway + R2DBC Coexistence Note

R2DBC is non-blocking and does not support JDBC. Flyway requires JDBC (blocking) for schema migrations. Both work together:
- `spring-boot-starter-data-r2dbc` provides the R2DBC connection pool (for the application)
- `org.postgresql:postgresql` JDBC driver provides the JDBC connection (for Flyway only, at startup)
- Flyway runs during Spring context initialization, before the application starts serving requests
- No conflict — they use separate datasource beans configured separately

### Coverage Gate (JaCoCo)

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <haltOnFailure>true</haltOnFailure>
                <rules>
                    <rule>
                        <limits>
                            <limit><counter>INSTRUCTION</counter><minimum>0.80</minimum></limit>
                            <limit><counter>BRANCH</counter><minimum>0.80</minimum></limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Test Naming Convention

Following the project testing standards:
- Method name: `should_<outcome>_when_<condition>()`
- Structure: Given / When / Then comments inside test body
- Unit tests: `@ExtendWith(MockitoExtension.class)`
- Integration tests: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `WebTestClient`

