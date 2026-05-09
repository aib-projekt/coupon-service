# Specification: Coupon Service REST API

*Authoritative implementation reference combining all approved product design, gap analysis decisions, and technical clarifications. Implementers should not need any other document.*

---

## Goal

Implement a production-ready greenfield REST API microservice for coupon lifecycle management using Spring Boot 3 + WebFlux (reactive, non-blocking), PostgreSQL + Spring Data R2DBC, Flyway migrations (JDBC coexisting with R2DBC), atomic SQL concurrency control, ip-api.com geolocation enforcement (fail closed), and configurable per-user single-use enforcement.

---

## User Stories

- As a marketing operator, I want to create a coupon with a use limit and country restriction so that I can run targeted promotional campaigns.
- As a customer, I want to redeem a coupon so that I receive a discount when shopping from an eligible country.
- As a customer, I want to see the current state of a coupon (remaining uses) so that I know whether it is still valid before attempting redemption.
- As a system administrator, I want concurrent redemptions to be handled atomically so that the coupon is never over-redeemed beyond its configured limit.

---

## Core Requirements

1. **POST /api/v1/coupons** — create a coupon with code, maxUses, country (ISO 3166-1 alpha-2), and optional perUserLimit flag; respond 201 Created
2. **GET /api/v1/coupons/{code}** — retrieve coupon state including currentUses; normalize code to uppercase before lookup; respond 200 OK
3. **POST /api/v1/coupons/{code}/redeem** — redeem a coupon following the 7-step ordered flow; respond 200 OK with remainingUses
4. All coupon codes normalized to UPPERCASE at the API boundary before any lookup or storage
5. Atomic SQL UPDATE enforces `current_uses < max_uses` — no over-redemption under any concurrency level
6. Geolocation via ip-api.com (first non-private IP from X-Forwarded-For header); fail closed on any geo failure (503 GEO_UNAVAILABLE)
7. When perUserLimit=true: userId required in redeem body, UNIQUE(coupon_code, user_id) enforced at DB level with named constraint `uq_coupon_usages_per_user`
8. Structured error responses: `{"error": "CODE", "message": "..."}` for all error cases
9. JaCoCo 80% instruction + branch coverage gate, haltOnFailure=true
10. Swagger UI available at /swagger-ui.html (minimal @OpenAPIDefinition only, no per-endpoint annotations)

---

## Visual Design

None — REST API only, no UI.

---

## Reusable Components

### Existing Code to Leverage

This is a fully greenfield project. The `src/` directories are completely empty. The sole existing file relevant to implementation is:

- `/Users/bartek/Documents/Projects/AiB/rekrutacje/empik-coupon-service/pom.xml` — bare skeleton to be fully replaced (groupId=`pl.aibprojekt`, artifactId=`recruitment`, Java 25). Must be rewritten with Spring Boot 3.3.x parent, all dependencies, plugins, and profiles.

Standards documents that inform implementation conventions (not code to reuse, but patterns to follow):

- `.maister/docs/standards/backend/spring-boot.md` — Spring Boot 3.3.x BOM, `${VAR:default}` config, ISO-8601 Jackson, actuator always exposed
- `.maister/docs/standards/backend/build.md` — Maven 3.8+ enforcer, iCloud.nosync profile, BOM-managed versions
- `.maister/docs/standards/backend/api.md` — `/api/{domain}/{resource}` base path, plural resource nouns, ResponseEntity with typed DTOs
- `.maister/docs/standards/backend/naming-conventions.md` — PascalCase with layer suffixes, `Dto` suffix on DTOs, Java records for simple DTOs
- `.maister/docs/standards/backend/migrations.md` — small focused migrations, descriptive names, never modify committed migrations
- `.maister/docs/standards/backend/logging.md` — dual-level exception logging, MDC enrichment
- `.maister/docs/standards/backend/docker.md` — non-root user `spring:spring` (UID 10001), HEALTHCHECK via actuator
- `.maister/docs/standards/global/java-conventions.md` — Java 25, `jakarta.*` packages, UTF-8, English only
- `.maister/docs/standards/global/error-handling.md` — clear user messages, no internal detail leakage, centralized handling
- `.maister/docs/standards/global/minimal-implementation.md` — build only what is called, no speculative abstractions
- `.maister/docs/standards/global/validation.md` — server-side validation, validate early, structured error responses
- `.maister/docs/standards/testing/jacoco.md` — 80% gate, haltOnFailure, excluded: model/dto/config/Application class
- `.maister/docs/standards/testing/junit-patterns.md` — Given/When/Then structure, `methodUnderTest_shouldBehavior` naming, null-safe `argThat`

### New Components Required

All ~25+ Java source files, 2 SQL migrations, pom.xml, application.yml, application-test.yml, Dockerfile, docker-compose.yml, and test classes must be created. There is no existing code to reuse — this is a greenfield service. Every component is justified by a direct caller in the specified architecture.

---

## Technical Approach

### Architecture: Four-Layer Layered Microservice

```
pl.aibprojekt.couponservice/
├── api/
│   ├── CouponController.java
│   ├── GlobalExceptionHandler.java
│   ├── dto/
│   │   ├── CreateCouponRequest.java
│   │   ├── RedeemCouponRequest.java
│   │   ├── CouponResponse.java
│   │   ├── RedemptionResponse.java
│   │   └── ErrorResponse.java
│   └── validation/
│       └── CountryCodeValidator.java          (+ @ValidCountryCode annotation interface)
├── application/
│   ├── CouponService.java                     (interface)
│   ├── CouponServiceImpl.java
│   └── GeoLocationService.java                (interface — port)
├── domain/
│   ├── Coupon.java                            (@Table("coupons") record)
│   ├── CouponUsage.java                       (@Table("coupon_usages") record)
│   ├── CouponErrorCode.java                   (enum with HttpStatus)
│   └── CouponException.java                   (RuntimeException with CouponErrorCode)
└── infrastructure/
    ├── persistence/
    │   ├── CouponRepository.java              (R2dbcRepository<Coupon, String> + CouponRepositoryCustom)
    │   ├── CouponRepositoryCustom.java        (interface with atomicIncrementUsage)
    │   ├── CouponRepositoryCustomImpl.java    (DatabaseClient-based atomic UPDATE)
    │   └── CouponUsageRepository.java         (R2dbcRepository<CouponUsage, Long>)
    ├── geo/
    │   └── IpApiGeoLocationService.java       (WebClient-based GeoLocationService impl)
    └── config/
        ├── GeoLocationConfig.java             (@Configuration — GeoLocationService bean)
        ├── WebClientConfig.java               (@Configuration — WebClient.Builder bean)
        └── GeoProperties.java                 (@ConfigurationProperties(prefix = "geo") record)

IpAddressExtractor.java                        (in infrastructure/ — X-Forwarded-For parsing)
CouponServiceApplication.java                  (in root package — Spring Boot entry point)
```

**Layer dependency rule**: api → application → domain; infrastructure → application (implements interfaces); domain has zero external dependencies.

### Data Model

#### Flyway Migration V1 — `src/main/resources/db/migration/V1__create_coupons_table.sql`

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

#### Flyway Migration V2 — `src/main/resources/db/migration/V2__create_coupon_usages_table.sql`

```sql
CREATE TABLE coupon_usages (
    id           BIGSERIAL    PRIMARY KEY,
    coupon_code  VARCHAR(64)  NOT NULL REFERENCES coupons(code),
    user_id      VARCHAR(255) NOT NULL,
    used_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_coupon_usages_per_user UNIQUE (coupon_code, user_id)
);
```

The constraint is named `uq_coupon_usages_per_user` (not anonymous) to enable reliable discrimination in `GlobalExceptionHandler`.

#### Domain Records (Spring Data R2DBC)

```java
// domain/Coupon.java
@Table("coupons")
public record Coupon(
    @Id String code,
    int maxUses,
    int currentUses,
    String country,
    boolean perUserLimit,
    Instant createdAt
) {}

// domain/CouponUsage.java
@Table("coupon_usages")
public record CouponUsage(
    @Id Long id,
    String couponCode,
    String userId,
    Instant usedAt
) {}
```

#### Error Enum

```java
// domain/CouponErrorCode.java
public enum CouponErrorCode {
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND),
    COUPON_EXHAUSTED(HttpStatus.CONFLICT),
    COUNTRY_NOT_ALLOWED(HttpStatus.FORBIDDEN),
    ALREADY_USED(HttpStatus.CONFLICT),
    GEO_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    COUPON_ALREADY_EXISTS(HttpStatus.CONFLICT),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST);

    private final HttpStatus httpStatus;

    CouponErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
}
```

#### Exception

```java
// domain/CouponException.java
public class CouponException extends RuntimeException {
    private final CouponErrorCode errorCode;

    public CouponException(CouponErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CouponErrorCode getErrorCode() { return errorCode; }
}
```

### API Contract

#### POST /api/v1/coupons — Create Coupon

Request body (JSON):

| Field | Type | Required | Validation |
|---|---|---|---|
| `code` | String | Yes | Pattern `[A-Za-z0-9]{1,64}` |
| `maxUses` | int | Yes | >= 1 |
| `country` | String | Yes | Valid ISO 3166-1 alpha-2 code (custom `@ValidCountryCode` annotation) |
| `perUserLimit` | boolean | No | Defaults to false |

Responses:
- `201 Created` — full coupon state: `{code, maxUses, currentUses, country, perUserLimit, createdAt}`
- `400 Bad Request` — `{"error": "INVALID_REQUEST", "message": "..."}`
- `409 Conflict` — `{"error": "COUPON_ALREADY_EXISTS", "message": "Coupon with code 'X' already exists."}`

#### GET /api/v1/coupons/{code} — Get Coupon

- Code path parameter normalized to uppercase before lookup.
- `200 OK` — `{code, maxUses, currentUses, country, perUserLimit, createdAt}`
- `404 Not Found` — `{"error": "COUPON_NOT_FOUND", "message": "Coupon 'X' not found."}`

#### POST /api/v1/coupons/{code}/redeem — Redeem Coupon

Request headers: `X-Forwarded-For: <ip1>, <ip2>, ...`
Request body (JSON): `{"userId": "user-abc-123"}` (userId required only when perUserLimit=true)

`200 OK` — `{code, remainingUses, redeemedAt}`

Error responses:

| HTTP | Error Code | Condition |
|---|---|---|
| 400 | `INVALID_REQUEST` | Missing userId when perUserLimit=true; malformed body |
| 403 | `COUNTRY_NOT_ALLOWED` | Client IP resolves to country != coupon.country |
| 404 | `COUPON_NOT_FOUND` | Code does not exist |
| 409 | `COUPON_EXHAUSTED` | Atomic UPDATE finds current_uses >= max_uses |
| 409 | `ALREADY_USED` | coupon_usages row exists for (couponCode, userId) |
| 503 | `GEO_UNAVAILABLE` | ip-api.com unreachable, timeout, HTTP error, or status=fail |

#### Error Response Format (all errors)

```json
{"error": "COUPON_NOT_FOUND", "message": "Coupon 'SPRING20' not found."}
```

### Redemption Flow — 7 Steps (ordering is mandatory)

Steps 1–5 execute OUTSIDE any database transaction to minimize lock window and keep the geo HTTP call out of the transaction.

| Step | Action | Failure → Response |
|---|---|---|
| 1 | Normalize `code` to uppercase | — |
| 2 | Extract client IP from `X-Forwarded-For` — first non-private IP (RFC 1918 + loopback ranges); fallback to request remote address if header absent/all-private | — |
| 3 | SELECT coupon by code | Not found → 404 COUPON_NOT_FOUND |
| 4 | Call `GeoLocationService.getCountryCode(ip)` | Any error → 503 GEO_UNAVAILABLE |
| 4b | Compare returned countryCode with coupon.country | Mismatch → 403 COUNTRY_NOT_ALLOWED |
| 5 | If `perUserLimit=false` → skip to Step 6 | — |
| 5a | Validate userId present in request body | Missing → 400 INVALID_REQUEST |
| 5b | SELECT from coupon_usages WHERE (couponCode, userId) | Row exists → 409 ALREADY_USED |
| 6 | **BEGIN REACTIVE TRANSACTION** — wrap Steps 6a+6b using `TransactionalOperator.transactional(...)` (NOT `@Transactional` on the whole method — that would wrap the geo HTTP call in the transaction) | — |
| 6a | `UPDATE coupons SET current_uses = current_uses + 1 WHERE code = :code AND current_uses < max_uses`; check rowsUpdated | 0 rows → 409 COUPON_EXHAUSTED + ROLLBACK |
| 6b | If `perUserLimit=true`: `INSERT INTO coupon_usages (coupon_code, user_id, used_at)` | UNIQUE violation on `uq_coupon_usages_per_user` → 409 ALREADY_USED + ROLLBACK |
| 6c | **COMMIT** | — |
| 7 | Return `200 OK`: `{code, remainingUses = maxUses - (currentUses + 1), redeemedAt}` — use the `currentUses` value read in Step 3 (pre-update snapshot); do NOT re-fetch the coupon after the UPDATE | — |

**Critical invariants**:
1. Geolocation is called BEFORE the atomic UPDATE (step 4 before step 6) — avoids incrementing the counter if geo fails
2. Transaction scope is minimal (Steps 6a/6b only)
3. Step 5b is an optimization fast path; the DB constraint in 6b is the concurrency safety net

### Private IP Ranges (IpAddressExtractor)

Skip IPs matching these ranges when processing X-Forwarded-For:
- `10.0.0.0/8`
- `172.16.0.0/12`
- `192.168.0.0/16`
- `127.0.0.0/8`
- `::1` (IPv6 loopback)

### Geolocation Integration

#### GeoLocationService Interface (in `application/`)

```java
public interface GeoLocationService {
    Mono<String> getCountryCode(String ipAddress);
    // Returns ISO 3166-1 alpha-2 country code on success
    // Signals failure via Mono.error(new CouponException(GEO_UNAVAILABLE, "..."))
}
```

#### ip-api.com Request

```
GET http://ip-api.com/json/{ip}?fields=status,countryCode
```

Success response: `{"status": "success", "countryCode": "PL"}`

All failure conditions map to `GEO_UNAVAILABLE (503)`:

| Condition | Handling |
|---|---|
| Network timeout | `Mono.error(CouponException(GEO_UNAVAILABLE))` |
| HTTP non-2xx response | Same |
| `status = "fail"` in JSON body | Same |
| `countryCode` blank or null | Same |

WebClient configuration:
- Connect timeout: 2 seconds (`geo.connect-timeout-seconds`)
- Read timeout: 3 seconds (`geo.read-timeout-seconds`)

#### Configuration Split (confirmed in gap analysis)

Two separate `@Configuration` classes:
- `GeoLocationConfig` — declares the `GeoLocationService` bean (`IpApiGeoLocationService`)
- `WebClientConfig` — declares the `WebClient.Builder` bean

`IpApiGeoLocationService` is the only class that knows about ip-api.com. Swapping providers requires only changing the `@Bean` definition in `GeoLocationConfig`.

#### GeoProperties

A `@ConfigurationProperties(prefix = "geo")` record binding:
- `baseUrl` (String) — maps from `geo.base-url`
- `connectTimeoutSeconds` (int) — maps from `geo.connect-timeout-seconds`
- `readTimeoutSeconds` (int) — maps from `geo.read-timeout-seconds`

### Exception Handling (GlobalExceptionHandler)

`@ControllerAdvice` class in `api/` package:

| Exception | Mapping |
|---|---|
| `CouponException` | `{error: errorCode.name(), message: ex.getMessage()}` + errorCode.getHttpStatus() |
| `WebExchangeBindException` (bean validation) | 400 INVALID_REQUEST |
| `DataIntegrityViolationException` where message contains `uq_coupon_usages_per_user` | 409 ALREADY_USED |
| `DataIntegrityViolationException` where message does NOT contain `uq_coupon_usages_per_user` | 409 COUPON_ALREADY_EXISTS |
| `Exception` (catch-all) | 500 — no internal details in response body |

Discrimination of `DataIntegrityViolationException`: check `exception.getMessage().contains("uq_coupon_usages_per_user")` — true → ALREADY_USED; false → COUPON_ALREADY_EXISTS (a coupons table PK violation). Using the negative match avoids hardcoding the auto-generated constraint name `coupons_pkey` which may vary across PostgreSQL versions or schema dumps.

### Coupon Creation Business Rules

1. `code` normalized to uppercase before INSERT
2. `code` must match `[A-Za-z0-9]{1,64}` — validated by Bean Validation annotation at controller
3. `country` must be a valid ISO 3166-1 alpha-2 code — validated by custom `@ValidCountryCode` constraint annotation backed by a static list in `CountryCodeValidator`
4. `maxUses` >= 1
5. If PK violation on INSERT → 409 COUPON_ALREADY_EXISTS (via GlobalExceptionHandler)

### Interface Signatures

```java
// application/CouponService.java
public interface CouponService {
    Mono<CouponResponse> createCoupon(CreateCouponRequest request);
    Mono<CouponResponse> getCoupon(String code);
    Mono<RedemptionResponse> redeemCoupon(String code, String clientIp, RedeemCouponRequest request);
}

// infrastructure/persistence/CouponRepositoryCustom.java
public interface CouponRepositoryCustom {
    Mono<Integer> atomicIncrementUsage(String couponCode);
    // Returns 1 if incremented, 0 if cap reached or code not found
}

// infrastructure/persistence/CouponRepository.java
public interface CouponRepository extends R2dbcRepository<Coupon, String>, CouponRepositoryCustom {
    // Inherits: findById, save, delete (basic CRUD)
    // Atomic counter update delegated to CouponRepositoryCustomImpl
}

// infrastructure/persistence/CouponUsageRepository.java
public interface CouponUsageRepository extends R2dbcRepository<CouponUsage, Long> {
    Mono<Boolean> existsByCouponCodeAndUserId(String couponCode, String userId);
}
```

### DTO Definitions

All DTOs are Java records. No Lombok.

```java
// api/dto/CreateCouponRequest.java
public record CreateCouponRequest(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9]{1,64}") String code,
    @Min(1) int maxUses,
    @NotBlank @ValidCountryCode String country,
    boolean perUserLimit
) {}

// api/dto/RedeemCouponRequest.java
public record RedeemCouponRequest(String userId) {}

// api/dto/CouponResponse.java
public record CouponResponse(
    String code, int maxUses, int currentUses,
    String country, boolean perUserLimit, Instant createdAt
) {}

// api/dto/RedemptionResponse.java
public record RedemptionResponse(String code, int remainingUses, Instant redeemedAt) {}

// api/dto/ErrorResponse.java
public record ErrorResponse(String error, String message) {}
```

### pom.xml Structure

- **Parent**: `spring-boot-starter-parent` 3.3.x (latest stable)
- **artifactId**: `coupon-service`
- **groupId**: `pl.aibprojekt`
- **Java version**: `25`
- **Encoding**: `UTF-8`

Dependencies (BOM-managed versions, no inline `<version>` tags except where required):

| Dependency | Scope | Purpose |
|---|---|---|
| `spring-boot-starter-webflux` | compile | Reactive HTTP stack + WebClient |
| `spring-boot-starter-data-r2dbc` | compile | Spring Data R2DBC |
| `spring-boot-starter-validation` | compile | Jakarta Bean Validation |
| `spring-boot-starter-actuator` | compile | /actuator/health |
| `io.asyncer:r2dbc-postgresql` | compile | R2DBC PostgreSQL driver |
| `org.postgresql:postgresql` | compile | JDBC driver (Flyway only) |
| `org.flywaydb:flyway-core` | compile | Schema migration engine |
| `org.flywaydb:flyway-database-postgresql` | compile | PostgreSQL Flyway dialect |
| `springdoc-openapi-starter-webflux-ui` | compile | Swagger UI + OpenAPI docs |
| `spring-boot-starter-test` | test | JUnit 5 + Mockito + AssertJ |
| `io.projectreactor:reactor-test` | test | StepVerifier |
| `org.testcontainers:postgresql` | test | Real PostgreSQL in integration tests |
| `org.testcontainers:junit-jupiter` | test | Testcontainers JUnit 5 support |
| `org.testcontainers:r2dbc` | test | Testcontainers R2DBC support |

Plugins:

| Plugin | Purpose |
|---|---|
| `maven-enforcer-plugin` | Require Maven 3.8+, Java 25; ban `javax.*` imports |
| `maven-compiler-plugin` | Java 25 source/target |
| `maven-surefire-plugin` | Unit tests (exclude integration tests) |
| `maven-failsafe-plugin` | Integration tests |
| `jacoco-maven-plugin` | 80% instruction + branch coverage gate |

iCloud.nosync profile (macOS only):

```xml
<profile>
    <id>iCloud.nosync</id>
    <build>
        <directory>${project.basedir}/target.nosync</directory>
    </build>
</profile>
```

### application.yml

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
  jackson:
    serialization:
      write-dates-as-timestamps: false

geo:
  base-url: ${GEO_BASE_URL:http://ip-api.com}
  connect-timeout-seconds: ${GEO_CONNECT_TIMEOUT:2}
  read-timeout-seconds: ${GEO_READ_TIMEOUT:3}

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always
```

Do NOT include the `spring.autoconfigure.exclude` DynamoDB/JPA exclusion rule — that rule in `spring-boot.md` was written for a different project and does not apply here. Flyway requires the JDBC DataSource auto-configuration path.

### application-test.yml (for integration tests)

Use `@DynamicPropertySource` in the integration test base class to inject Testcontainers-managed URLs at runtime. The `application-test.yml` file provides static overrides that are safe defaults for test execution:

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/couponservice_test
    username: test
    password: test
  flyway:
    url: jdbc:postgresql://localhost:5432/couponservice_test
    username: test
    password: test
```

The `@DynamicPropertySource` method in the test base class overwrites `spring.r2dbc.url` and `spring.flyway.url` with the actual Testcontainers port before the Spring context starts. `GeoLocationService` is mocked via `@MockBean` in the test class — no live geo calls during tests.

### Docker

**Dockerfile** — override the `docker.md` standard's `eclipse-temurin:21-jre` reference; use `eclipse-temurin:25-jre` (project requires Java 25):

```dockerfile
FROM eclipse-temurin:25-jre
RUN addgroup --system spring && adduser --system spring --ingroup spring
USER spring:spring
COPY target/coupon-service-1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

**docker-compose.yml**:

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

### Flyway + R2DBC Coexistence

Both JDBC and R2DBC are required simultaneously:
- `spring-boot-starter-data-r2dbc` → R2DBC connection pool for the application (all reactive queries)
- `org.postgresql:postgresql` JDBC driver → used exclusively by Flyway at startup for schema migrations
- Both are configured separately in `application.yml` (`spring.r2dbc.*` and `spring.flyway.*`)
- No conflict: Spring Boot wires JDBC DataSource for Flyway separately from R2DBC ConnectionFactory for data access

---

## Implementation Guidance

### File Creation Order (dependency-safe)

1. `pom.xml` — full Spring Boot 3.3.x parent POM with all dependencies, plugins, profiles
2. `src/main/resources/db/migration/V1__create_coupons_table.sql`
3. `src/main/resources/db/migration/V2__create_coupon_usages_table.sql`
4. `src/main/resources/application.yml`
5. Domain layer: `Coupon`, `CouponUsage`, `CouponErrorCode`, `CouponException`
6. Infrastructure persistence: `CouponRepositoryCustom`, `CouponRepositoryCustomImpl`, `CouponRepository`, `CouponUsageRepository`
7. Application layer: `GeoLocationService` (interface), `CouponService` (interface), `CouponServiceImpl`
8. Infrastructure geo: `GeoProperties`, `IpAddressExtractor`, `IpApiGeoLocationService`, `GeoLocationConfig`, `WebClientConfig`
9. API layer: DTOs, `CountryCodeValidator` + `@ValidCountryCode`, `CouponController`, `GlobalExceptionHandler`
10. Entry point: `CouponServiceApplication` with `@OpenAPIDefinition`
11. Unit tests: `CouponServiceImplTest`, `IpApiGeoLocationServiceTest`, `IpAddressExtractorTest`
12. Integration tests: `CouponControllerIT`
13. `Dockerfile`, `docker-compose.yml`

### Reactive Transaction Scope

Use `TransactionalOperator` (injected into `CouponServiceImpl`) to wrap only Steps 6a+6b:

```java
return transactionalOperator.transactional(
    couponRepository.atomicIncrementUsage(code)
        .flatMap(rows -> /* handle 0-rows case + optional INSERT */)
);
```

Do NOT annotate `redeemCoupon()` with `@Transactional` — in reactive Spring, that annotation wraps the entire `Mono` chain including the geo HTTP call in Steps 1–5, which would hold an open DB transaction during the external HTTP request.

### Concurrency Strategy Detail

The atomic UPDATE:
```sql
UPDATE coupons
SET current_uses = current_uses + 1
WHERE code = :code
  AND current_uses < max_uses
```
Returns affected row count. If rowsUpdated == 0 after a successful SELECT in Step 3, it means the coupon was exhausted between Steps 3 and 6a → throw `CouponException(COUPON_EXHAUSTED)`.

The Step 5b SELECT is an optimization (fast path to reject known-duplicate before the transaction). The DB UNIQUE constraint on `uq_coupon_usages_per_user` is the authoritative race condition guard.

### Logging

Follow `logging.md` dual-level pattern:
- `ERROR` level with message summary for business exceptions caught in GlobalExceptionHandler
- `DEBUG` level with full stacktrace for diagnostic purposes
- No logging of sensitive data (userIds in redeem paths should be logged at DEBUG only)

### SpringDoc / OpenAPI

Only `@OpenAPIDefinition` at the application class level:

```java
@OpenAPIDefinition(info = @Info(title = "Coupon Service API", version = "v1"))
```

No `@Operation`, `@ApiResponse`, or `@Schema` annotations on individual endpoints or DTOs. Swagger UI auto-generates from WebFlux routes.

### Testing Approach

**2-8 focused tests per implementation step group.**

#### Unit Tests (JUnit 5 + MockitoExtension — no Spring context)

**`CouponServiceImplTest`** (8 test methods):
- `redeemCoupon_shouldReturnRedemptionResponse_whenSuccessfulRedemption()`
- `redeemCoupon_shouldReturnRedemptionResponse_whenPerUserLimitFalseAndUserIdNotProvided()`
- `redeemCoupon_shouldThrowCouponNotFoundException_whenCodeDoesNotExist()`
- `redeemCoupon_shouldThrowGeoUnavailable_whenGeoServiceFails()`
- `redeemCoupon_shouldThrowCountryNotAllowed_whenCountryMismatch()`
- `redeemCoupon_shouldThrowInvalidRequest_whenPerUserLimitTrueAndUserIdMissing()`
- `redeemCoupon_shouldThrowAlreadyUsed_whenUserHasAlreadyRedeemedCoupon()`
- `redeemCoupon_shouldThrowCouponExhausted_whenAtomicUpdateAffectsZeroRows()`

Also: `createCoupon_shouldReturnCouponResponse_whenSuccess()`, `getCoupon_shouldReturnCouponResponse_whenFound()`, `getCoupon_shouldThrowCouponNotFoundException_whenNotFound()`

**`IpApiGeoLocationServiceTest`** (4 test methods):
- `getCountryCode_shouldReturnCountryCode_whenSuccessResponse()`
- `getCountryCode_shouldThrowGeoUnavailable_whenNetworkTimeout()`
- `getCountryCode_shouldThrowGeoUnavailable_whenStatusIsFail()`
- `getCountryCode_shouldThrowGeoUnavailable_whenNon2xxHttpResponse()`

**`IpAddressExtractorTest`** (covering all extraction/fallback branches)

All unit tests:
- `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks`
- `// Given / When / Then` comment structure in every test body
- Null-safe `argThat` guards: `argThat(p -> p != null && ...)`
- Use `StepVerifier` for reactive assertions

#### Integration Tests (`@SpringBootTest` + Testcontainers PostgreSQL)

`GeoLocationService` mocked via `@MockBean` in all integration tests — no external HTTP calls.
Never use H2 — always Testcontainers real PostgreSQL.

**`POST /api/v1/coupons`** (5 scenarios):
- `should_create_coupon_and_return_201()`
- `should_return_409_when_coupon_code_already_exists()`
- `should_return_400_when_maxUses_is_zero()`
- `should_return_400_when_country_is_invalid_iso_code()`
- `should_normalize_code_to_uppercase_on_create()`

**`GET /api/v1/coupons/{code}`** (3 scenarios):
- `should_return_coupon_details_with_200()`
- `should_return_404_when_coupon_not_found()`
- `should_find_coupon_regardless_of_input_case()`

**`POST /api/v1/coupons/{code}/redeem`** (8 scenarios):
- `should_redeem_coupon_and_return_200_with_remainingUses()`
- `should_return_404_for_nonexistent_coupon()`
- `should_return_403_when_country_does_not_match()`
- `should_return_503_when_geolocation_service_unavailable()`
- `should_return_409_COUPON_EXHAUSTED_when_max_uses_reached()`
- `should_return_409_ALREADY_USED_when_user_redeems_twice_with_perUserLimit_true()`
- `should_allow_multiple_redemptions_by_same_user_when_perUserLimit_false()`
- `should_return_400_when_userId_missing_and_perUserLimit_true()`

**Concurrency tests** (2 scenarios — use `CountDownLatch` or `CompletableFuture.allOf()`):
- `should_not_exceed_maxUses_under_concurrent_redemptions()` — N=20 parallel redemptions on maxUses=10; assert currentUses == 10 exactly after
- `should_return_ALREADY_USED_on_concurrent_per_user_duplicate()` — N=10 concurrent same-user redemptions on perUserLimit=true; assert exactly 1 succeeds

Integration tests use `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `WebTestClient`.

#### JaCoCo Configuration

80% minimum instruction AND branch coverage, `haltOnFailure=true`.

Excluded from coverage gate (per `.maister/docs/standards/testing/jacoco.md`):
- `**/domain/**` (model/entity packages)
- `**/*Request.class`, `**/*Response.class` (DTO records)
- `**/config/**` (configuration classes)
- `**/*Application.class` (Spring Boot entry point)

Covered packages that must hit 80%: `application/`, `api/` (controller, exception handler, validation), `infrastructure/` (repositories, geo service, IP extractor).

### Standards Conflicts Resolved

| Conflict | Resolution |
|---|---|
| `docker.md` references `eclipse-temurin:21-jre` | Use `eclipse-temurin:25-jre` — this project requires Java 25 (superseded) |
| `spring-boot.md` "No JPA/JDBC Auto-configuration" rule | Do NOT apply — this was written for a DynamoDB-only service; Flyway requires JDBC DataSource |
| `lombok.md` standards | Not applicable — Java records + explicit constructors used throughout (per Q2 clarification) |

---

## Standards Compliance

| Standard | Application |
|---|---|
| `global/java-conventions.md` | Java 25, `jakarta.*` packages enforced by maven-enforcer, UTF-8, English |
| `global/minimal-implementation.md` | Every method has a direct caller; no speculative abstractions; no future stubs |
| `global/validation.md` | Server-side validation with Bean Validation annotations; early rejection before DB access |
| `global/error-handling.md` | Typed exceptions (`CouponException`), centralized `GlobalExceptionHandler`, no internal detail leakage |
| `backend/spring-boot.md` | Spring Boot 3.3.x BOM, `${VAR:default}` config placeholders, ISO-8601 Jackson, actuator health exposed |
| `backend/build.md` | Maven 3.8+ enforcer, iCloud.nosync profile, BOM-managed dependency versions |
| `backend/api.md` | `/api/v1/coupons` base path, plural resource nouns, typed DTO responses |
| `backend/naming-conventions.md` | PascalCase + layer suffix, Java records for DTOs, `application/` interface pattern |
| `backend/migrations.md` | Small focused migrations (V1/V2), descriptive names, named constraints, never modify committed |
| `backend/logging.md` | Dual-level exception logging (ERROR summary, DEBUG stacktrace), no sensitive data in production logs |
| `backend/docker.md` | Non-root `spring:spring` user (UID 10001), HEALTHCHECK via actuator; base image overridden to 25-jre |
| `testing/jacoco.md` | 80% instruction + branch gate, haltOnFailure, excluded: domain/dto/config/Application |
| `testing/junit-patterns.md` | `methodUnderTest_shouldBehavior` naming, Given/When/Then structure, MockitoExtension, null-safe argThat |
| `testing/test-writing.md` | Test behavior not implementation, meaningful test names, test isolation |

---

## Out of Scope

- Authentication or authorization (no API keys, no JWT)
- Rate limiting
- Response caching layer
- Admin endpoints (bulk coupon operations, usage reports)
- Distributed tracing or metrics instrumentation
- GeoLite2 or alternative geolocation providers (interface exists but only ip-api.com is implemented)
- Coupon expiry dates or time-bounded validity
- Multi-country coupons (single country per coupon only)

---

## Success Criteria

1. All three endpoints return correct HTTP status codes and JSON body structure for every documented scenario
2. Concurrent test: N=20 requests on maxUses=10 results in exactly 10 successes and 10 COUPON_EXHAUSTED responses — no over-redemption
3. Concurrent per-user test: N=10 same-user requests on perUserLimit=true results in exactly 1 success
4. `mvn verify` (with `-P iCloud.nosync` on macOS) passes with JaCoCo >= 80% instruction and branch coverage and zero test failures
5. `docker-compose up` starts postgres + coupon-service; `/actuator/health` returns `{"status": "UP"}`; Flyway migrations run successfully on startup
6. Swagger UI accessible at `/swagger-ui.html`
7. Geolocation is never called when the coupon does not exist (Step 3 returns before Step 4)
8. currentUses field in GET response reflects the correct post-redemption count
