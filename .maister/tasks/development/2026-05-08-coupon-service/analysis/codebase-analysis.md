# Codebase Analysis Report

**Date**: 2026-05-08
**Task**: Coupon Service REST API Implementation
**Description**: Implement a production-ready REST API for coupon lifecycle management using Spring Boot 3 + WebFlux, PostgreSQL + Spring Data R2DBC, Flyway migrations, atomic SQL concurrency, ip-api.com geolocation (fail closed), and configurable per-user single-use enforcement. Three endpoints: POST /api/v1/coupons, GET /api/v1/coupons/{code}, POST /api/v1/coupons/{code}/redeem.
**Analyzer**: codebase-analyzer skill (1 Explore agent: File Discovery + Code Analysis combined)

---

## Summary

The project is a fully greenfield Java 25 Maven skeleton. The source tree has no application code whatsoever — all `src/` directories are empty. The existing scaffold provides the correct Maven wrapper (3.9.14), a minimal `pom.xml` (groupId `pl.aibprojekt`, artifactId `recruitment`, Java 25, UTF-8), and a rich set of 21 project standards files plus a complete, implementation-ready feature specification produced by a prior product-design workflow. Everything that needs to exist must be created from scratch; there is nothing to modify or migrate.

---

## Files Identified

### Primary Files

**/Users/bartek/Documents/Projects/AiB/rekrutacje/empik-coupon-service/pom.xml** (20 lines)
- Bare Maven descriptor: groupId `pl.aibprojekt`, artifactId `recruitment`, version `1.0-SNAPSHOT`, Java 25, UTF-8
- Must be replaced with a Spring Boot 3.3.x parent POM that declares all required dependencies and plugins

### Related Files

**/Users/bartek/Documents/Projects/AiB/rekrutacje/empik-coupon-service/.maister/docs/project/architecture.md**
- Defines the four-layer structure (api, application, domain, infrastructure) that the implementation must follow

**/Users/bartek/Documents/Projects/AiB/rekrutacje/empik-coupon-service/.maister/docs/project/tech-stack.md**
- Confirms Java 25, Maven wrapper, IntelliJ IDEA; marks Spring Boot, PostgreSQL, JUnit 5 as recommended-pending-finalization (now finalized by the task)

**/Users/bartek/Documents/Projects/AiB/rekrutacje/empik-coupon-service/.maister/docs/standards/backend/spring-boot.md**
- Spring Boot 3.3.x + Spring Cloud 2023.0.x BOM, four Spring profiles (local/det/iut/prod), actuator `/actuator/health` always exposed with `show-details: always`, ISO-8601 Jackson date serialization, all config via `${VAR_NAME:default}` placeholders

**/Users/bartek/Documents/Projects/AiB/rekrutacje/empik-coupon-service/.maister/docs/standards/backend/build.md**
- Maven 3.8+ required (enforcer), `iCloud.nosync` profile mandatory on macOS, BOM-managed dependency versions only

**/Users/bartek/Documents/Projects/AiB/rekrutacje/empik-coupon-service/.maister/docs/standards/backend/migrations.md**
- Flyway migrations: reversible, small/focused, zero-downtime aware, schema separate from data, descriptive names, never modify committed migrations

**/Users/bartek/Documents/Projects/AiB/rekrutacje/empik-coupon-service/.maister/docs/standards/backend/api.md**
- Base path `/api/{domain}/{resource}`, plural resource nouns, `ResponseEntity<T>` typed DTO bodies, proper HTTP status codes

**/Users/bartek/Documents/Projects/AiB/rekrutacije/empik-coupon-service/.maister/docs/standards/backend/naming-conventions.md**
- PascalCase with layer suffixes (Controller, Service, Repository, Mapper, Config, Aspect), feature-first packages under `pl.aibprojekt.{module}.{service}`, Java records for simple DTOs, `Optional<T>` for nullable returns, `Dto` suffix on all DTOs

**/Users/bartek/Documents/Projects/AiB/rekrutacije/empik-coupon-service/.maister/docs/standards/backend/models.md**
- Singular entity names, created_at/updated_at timestamps, DB constraints enforced at schema level, index foreign keys and frequently queried fields

**/Users/bartek/Documents/Projects/AiB/rekrutacije/empik-coupon-service/.maister/docs/standards/backend/lombok.md**
- Lombok `provided` scope, `addLombokGeneratedAnnotation=true` in `lombok.config`, `@Slf4j` for logging, `@RequiredArgsConstructor` for constructor injection, no `@Autowired` field injection

**/Users/bartek/Documents/Projects/AiB/rekrutacije/empik-coupon-service/.maister/docs/standards/backend/logging.md**
- `@Slf4j`, dual-level exception logging (ERROR for summary, DEBUG for stacktrace), MDC enrichment with `businessOperation`/`duration`/`errorType`, structured console pattern

**/Users/bartek/Documents/Projects/AiB/rekrutacije/empik-coupon-service/.maister/docs/standards/backend/docker.md**
- `eclipse-temurin:25-jre` base image (spec says 25-jre, not 21-jre — standard references 21 but java-conventions overrides to 25), non-root `spring:spring` user (UID 10001), HEALTHCHECK via actuator

**/Users/bartek/Documents/Projects/AiB/rekrutacje/empik-coupon-service/.maister/docs/standards/global/error-handling.md**
- Clear user messages without internal detail leakage, fail-fast validation, typed exceptions, centralized error handling at controller boundary

**/Users/bartek/Documents/Projects/AiB/rekrutacije/empik-coupon-service/.maister/docs/standards/global/java-conventions.md**
- Java 25 enforced by maven-enforcer-plugin, Jakarta EE (`jakarta.*`) packages only, English code/docs, UTF-8 encoding

**/Users/bartek/Documents/Projects/AiB/rekrutacije/empik-coupon-service/.maister/docs/standards/global/minimal-implementation.md**
- Build only what is called, delete exploration artifacts, no future stubs, no speculative abstractions

**/Users/bartek/Documents/Projects/AiB/rekrutacije/empik-coupon-service/.maister/docs/standards/global/validation.md**
- Server-side validation always required, validate early, constraint annotations, structured error responses

**/Users/bartek/Documents/Projects/AiB/rekrutacije/empik-coupon-service/.maister/docs/standards/testing/jacoco.md**
- 80% minimum instruction AND branch coverage, `haltOnFailure: true`, applied to `service`, `controller`, `repository`, `consumer`, `mapper`, `aspect`, `exception` packages; excludes `model`, `dto`, `config`, Lombok-generated, Application class

**/Users/bartek/Documents/Projects/AiB/rekrutacije/empik-coupon-service/.maister/docs/standards/testing/junit-patterns.md**
- `method_shouldBehavior` naming, `// Given / When / Then` comments mandatory, `@ExtendWith(MockitoExtension.class)` for unit tests, `argThat` null-safety guard (`p != null &&`)

**/Users/bartek/Documents/Projects/AiB/rekrutacije/empik-coupon-service/.maister/tasks/product-design/2026-05-08-coupon-service/analysis/feature-spec.md**
- Complete 7-section implementation specification: data model, API contract, redemption flow, service architecture, geolocation integration, testing strategy, infrastructure configuration — this is the primary implementation reference

**/Users/bartek/Documents/Projects/AiB/rekrutacije/empik-coupon-service/.maister/tasks/product-design/2026-05-08-coupon-service/outputs/product-brief.md**
- Acceptance criteria, personas, design decision summary, critical architectural invariants

---

## Current Functionality

There is no application functionality. The project is a pure scaffold — empty `src/main/java/`, `src/main/resources/`, and `src/test/java/` directories with a bare `pom.xml`. The entire implementation must be created.

### Key Components/Functions — To Be Created

All components are greenfield. Based on the feature spec, the following must be built:

**Domain layer** (`pl.aibprojekt.couponservice.domain`):
- `Coupon` — R2DBC `@Table("coupons")` record: `code` (PK, VARCHAR 64), `maxUses`, `currentUses`, `country` (CHAR 2), `perUserLimit`, `createdAt`
- `CouponUsage` — R2DBC `@Table("coupon_usages")` record: `id` (BIGSERIAL PK), `couponCode` (FK), `userId`, `usedAt`
- `CouponErrorCode` — enum with HTTP status: `COUPON_NOT_FOUND(404)`, `COUPON_EXHAUSTED(409)`, `COUNTRY_NOT_ALLOWED(403)`, `ALREADY_USED(409)`, `GEO_UNAVAILABLE(503)`, `COUPON_ALREADY_EXISTS(409)`, `INVALID_REQUEST(400)`
- `CouponException` — `RuntimeException` carrying `CouponErrorCode`

**Application layer** (`pl.aibprojekt.couponservice.application`):
- `CouponService` — interface: `createCoupon`, `getCoupon`, `redeemCoupon`
- `GeoLocationService` — interface (port): `Mono<String> getCountryCode(String ipAddress)`
- `CouponServiceImpl` — orchestrates redeem flow in mandatory 7-step order

**API layer** (`pl.aibprojekt.couponservice.api`):
- `CouponController` — WebFlux `@RestController` with three endpoints
- DTOs: `CreateCouponRequest`, `RedeemCouponRequest`, `CouponResponse`, `RedemptionResponse`, `ErrorResponse`
- `CountryCodeValidator` — custom `@ValidCountryCode` annotation against full ISO 3166-1 alpha-2 list

**Infrastructure layer** (`pl.aibprojekt.couponservice.infrastructure`):
- `CouponRepository` — `R2dbcRepository<Coupon, String>` + `CouponRepositoryCustom` interface
- `CouponRepositoryCustomImpl` — `DatabaseClient`-based `atomicIncrementUsage()` using `UPDATE ... WHERE current_uses < max_uses`
- `CouponUsageRepository` — `R2dbcRepository<CouponUsage, Long>` with `existsByCouponCodeAndUserId`
- `IpApiGeoLocationService` — `WebClient`-based implementation of `GeoLocationService`
- `GlobalExceptionHandler` — `@ControllerAdvice` mapping `CouponException`, `WebExchangeBindException`, `DataIntegrityViolationException`, and catch-all to structured `ErrorResponse`
- `WebClientConfig` — `WebClient.Builder` bean for ip-api.com

### Data Flow

```
HTTP Request
  → CouponController (normalize code to uppercase, extract X-Forwarded-For IP)
    → CouponServiceImpl
        Steps 1-5 (outside transaction):
          → CouponRepository.findById() — existence check
          → GeoLocationService.getCountryCode(ip) — ip-api.com HTTP call
          → [if perUserLimit] CouponUsageRepository.existsByCouponCodeAndUserId()
        Step 6 (inside @Transactional):
          → CouponRepositoryCustomImpl.atomicIncrementUsage() — atomic SQL UPDATE
          → [if perUserLimit] CouponUsageRepository.save() — INSERT
        → Return RedemptionResponse
  → JSON Response (via GlobalExceptionHandler on any CouponException)
```

---

## Dependencies

### Imports — What This Depends On (All To Be Added to pom.xml)

- `spring-boot-starter-webflux`: WebFlux reactive HTTP stack, WebClient
- `spring-boot-starter-data-r2dbc`: Spring Data R2DBC, `DatabaseClient`, `R2dbcRepository`
- `io.r2dbc:r2dbc-postgresql`: R2DBC PostgreSQL driver
- `org.postgresql:postgresql` (JDBC): required by Flyway at startup (Flyway does not support R2DBC)
- `org.flywaydb:flyway-core`: schema migration engine
- `org.flywaydb:flyway-database-postgresql`: PostgreSQL-specific Flyway support
- `spring-boot-starter-validation`: Jakarta Bean Validation (`@NotNull`, `@Min`, custom annotations)
- `spring-boot-starter-actuator`: `/actuator/health` endpoint
- `org.testcontainers:postgresql` (test): real PostgreSQL in integration tests
- `org.testcontainers:junit-jupiter` (test): Testcontainers JUnit 5 integration
- `io.projectreactor:reactor-test` (test): `StepVerifier` for reactive stream assertions
- `jacoco-maven-plugin`: 80% instruction/branch coverage gate

### Consumers — What Depends On This

None — this is a new standalone microservice with no upstream Java consumers.

**Consumer Count**: 0 files
**Impact Scope**: Low — greenfield, no existing callers

---

## Test Coverage

### Test Files — To Be Created

**Unit tests** (`src/test/java/pl/aibprojekt/couponservice/`):
- `CouponServiceImplTest` — 8 test cases covering all business logic branches in `redeemCoupon` (MockitoExtension, no Spring context)
- `IpApiGeoLocationServiceTest` — 4 test cases covering success, timeout, HTTP error, and `status=fail` (mocked WebClient)
- `CountryCodeValidatorTest` — validation of ISO codes and invalid inputs

**Integration tests** (`@SpringBootTest` + Testcontainers PostgreSQL, `WebTestClient`, `GeoLocationService` mocked via `@MockBean`):
- `POST /api/v1/coupons` — 5 scenarios including duplicate and validation failures
- `GET /api/v1/coupons/{code}` — 3 scenarios including case-insensitive lookup
- `POST /api/v1/coupons/{code}/redeem` — 8 scenarios covering all error codes
- Concurrency tests — 2 scenarios: max uses enforcement under parallel load, per-user duplicate race

### Coverage Assessment

- **Test count**: ~22+ named test methods (feature spec defines explicit method names)
- **Coverage target**: >= 80% instruction AND branch (JaCoCo gate, `haltOnFailure: true`)
- **Gaps**: None specified — all business branches are explicitly called out in the feature spec
- **Excluded from gate**: `model`, `dto`, `config` packages; Lombok-generated code; `Application` main class
- **H2 prohibition**: Integration tests must use real PostgreSQL via Testcontainers, never H2

---

## Coding Patterns

### Naming Conventions

- **Classes**: PascalCase with layer suffix — `CouponController`, `CouponServiceImpl`, `CouponRepository`, `IpApiGeoLocationService`, `GlobalExceptionHandler`, `WebClientConfig`
- **DTOs**: `Dto` suffix or explicit response naming — `CreateCouponRequest`, `CouponResponse`, `ErrorResponse`
- **Test methods**: `methodUnderTest_shouldExpectedBehavior` — e.g., `redeemCoupon_shouldReturnCouponExhausted_whenAtomicUpdateAffectsZeroRows()`
- **Packages**: `pl.aibprojekt.couponservice.{api|application|domain|infrastructure}`
- **Constants/enums**: SCREAMING_SNAKE_CASE — `COUPON_NOT_FOUND`, `GEO_UNAVAILABLE`

### Architecture Patterns

- **Style**: Functional reactive (WebFlux, Project Reactor) — all return types are `Mono<T>` or `Flux<T>`
- **State management**: Stateless application layer; all state in PostgreSQL
- **Dependency injection**: Constructor injection via Lombok `@RequiredArgsConstructor` + `private final` fields; no `@Autowired`
- **Exception handling**: Centralized via `@ControllerAdvice`; typed domain exceptions (`CouponException`) carry error code and HTTP status
- **Domain entities**: Java records (immutable, no Lombok needed for entity definition)
- **Transactions**: `@Transactional` on service method, minimal scope (only the atomic UPDATE + optional INSERT)
- **Interface segregation**: `GeoLocationService` interface in application layer; implementation in infrastructure; zero coupling of business logic to ip-api.com
- **Custom SQL**: `CouponRepositoryCustom` + `DatabaseClient` for the atomic increment that cannot be expressed as a Spring Data derived query

---

## Complexity Assessment

| Factor | Value | Level |
|--------|-------|-------|
| File count to create | ~20-25 Java files + resources + SQL + Docker files | High |
| Dependencies | 10 Maven dependencies + 3 plugins | Medium |
| Consumers | 0 existing callers | Low |
| Test coverage | 80% gate, ~22+ explicit test cases | Medium |
| Technology complexity | WebFlux + R2DBC + Flyway (JDBC coexistence) + Testcontainers | High |

### Overall: Moderate-to-Complex

The domain logic itself is simple (2 entities, 3 endpoints). Complexity comes from the reactive stack: WebFlux requires non-blocking programming throughout, R2DBC has no ORM convenience, the Flyway+R2DBC dual-driver coexistence requires deliberate configuration, and the custom `DatabaseClient` atomic query departs from the standard Spring Data repository pattern. The geolocation integration adds an external HTTP dependency that must be correctly mocked in tests and properly fail-closed in production. Concurrency tests with parallel threads add additional test complexity.

---

## Key Findings

### Strengths

- Complete, implementation-ready feature specification exists (7 sections, all design decisions resolved)
- All project standards are pre-documented and consistent — naming, testing, coverage, logging, Docker
- Greenfield slate: no legacy code to refactor, no migration risk, no backward compatibility constraints
- Critical architectural decisions are locked in: atomic SQL UPDATE pattern, minimal transaction scope, GeoLocationService interface, X-Forwarded-For IP extraction algorithm

### Concerns

- **Docker base image conflict**: `docker.md` standard specifies `eclipse-temurin:21-jre`, but `java-conventions.md` requires Java 25 and the feature spec Dockerfile uses `eclipse-temurin:25-jre`. The Java 25 variant must be used — the docker standard's `21-jre` reference appears to be from a prior project and has been superseded by this project's Java 25 requirement.
- **spring-boot.md has a DynamoDB-only exclusion**: The standard says to exclude `DataSourceAutoConfiguration` and `HibernateJpaAutoConfiguration` — this was written for a DynamoDB service and does NOT apply here. This exclusion must not be applied; Flyway requires the JDBC datasource auto-configuration path.
- **Flyway + R2DBC dual-driver setup**: Non-obvious configuration requiring separate JDBC DataSource (for Flyway) and R2DBC ConnectionFactory (for the application). Must be deliberately configured — Spring Boot does not wire this automatically when only R2DBC starters are present.
- **WebFlux + Bean Validation**: `spring-boot-starter-validation` works differently with WebFlux than with MVC. `@Valid` on request body in `@RestController` methods needs `WebExchangeBindException` handling (not `MethodArgumentNotValidException`), which the feature spec already accounts for in `GlobalExceptionHandler`.
- **iCloud.nosync Maven profile**: The build standard requires `-P iCloud.nosync` on macOS. This profile must be defined in `pom.xml` to redirect the build output directory.

### Opportunities

- Java 25 language features (records, pattern matching, sealed classes) align perfectly with the domain model — entities as records is already specified
- The `GeoLocationService` interface enables straightforward provider swap (ip-api.com → GeoLite2) with zero business logic changes
- The explicit test case list in the feature spec makes test writing deterministic — no guesswork about what to cover

---

## Impact Assessment

- **Primary changes**: Everything in `src/` plus `pom.xml` — full greenfield creation
- **Related changes**: None — no existing code is touched
- **Test updates**: All tests created from scratch

### Risk Level: Low-Medium

**Low**: No migration risk, no backward compatibility, no consumers to break, complete spec available.
**Medium**: WebFlux + R2DBC is a more complex stack than MVC + JPA; the Flyway JDBC/R2DBC coexistence is a known gotcha; ip-api.com is a live external service that must be correctly isolated in tests.

---

## Recommendations

This is a **new capability implementation** with a fully resolved specification. Follow these recommendations:

### Implementation Strategy

1. **Start with `pom.xml`**: Add Spring Boot 3.3.x parent POM, all dependencies from Section 7 of the feature spec, the `iCloud.nosync` profile (redirect `target` directory), `maven-enforcer-plugin` (Maven 3.8+, Java 25, ban `javax.*`), `maven-compiler-plugin` (Java 25, `--enable-preview` if needed), `jacoco-maven-plugin` with the 80% gate, and `maven-surefire-plugin`/`maven-failsafe-plugin` split.

2. **Flyway migration files first**: Create `src/main/resources/db/migration/V1__create_coupons_table.sql` and `V2__create_coupon_usages_table.sql` exactly as specified. The schema is the contract everything else depends on.

3. **Domain layer**: Create `Coupon`, `CouponUsage` records, `CouponErrorCode` enum, `CouponException`. No dependencies on other layers.

4. **Infrastructure persistence**: Create `CouponRepository`, `CouponUsageRepository`, `CouponRepositoryCustom` interface, `CouponRepositoryCustomImpl` with `DatabaseClient`-based atomic update.

5. **Application layer**: Create `GeoLocationService` interface, `CouponService` interface, `CouponServiceImpl` with the 7-step redeem flow in the exact order specified. Apply `@Transactional` only to the service method's DB write section (Steps 6a/6b).

6. **Infrastructure geo**: Create `IpApiGeoLocationService` implementing `GeoLocationService` via `WebClient`. IP extraction logic (first non-private IP from `X-Forwarded-For`) lives here or in a dedicated utility class.

7. **API layer**: Create DTOs as Java records, `CouponController` with three endpoints, `CountryCodeValidator` with a compile-time list of valid ISO 3166-1 alpha-2 codes, `GlobalExceptionHandler` mapping all exception types.

8. **Configuration**: Create `application.yml` with R2DBC, Flyway, geo, actuator config using `${VAR:default}` placeholders. Create `WebClientConfig`, `lombok.config`.

9. **Tests**: Write unit tests first (no Spring context), then integration tests with Testcontainers.

10. **Docker**: Create `Dockerfile` and `docker-compose.yml` using `eclipse-temurin:25-jre`, non-root user, actuator HEALTHCHECK.

### Backward Compatibility

Not applicable — new service.

### Testing Requirements

- Unit tests: `@ExtendWith(MockitoExtension.class)`, mock all external dependencies, Given/When/Then comments, null-safe `argThat` guards
- Integration tests: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `WebTestClient` + real PostgreSQL (Testcontainers) + `@MockBean GeoLocationService`
- Concurrency tests: use `CountDownLatch` or `CompletableFuture.allOf()` to fire N parallel requests; assert exactly `maxUses` succeed

### Standards Conflicts to Resolve

- `docker.md` says `eclipse-temurin:21-jre` — use `eclipse-temurin:25-jre` instead (project requires Java 25)
- `spring-boot.md` says exclude `DataSourceAutoConfiguration` — do NOT apply this exclusion (Flyway needs JDBC DataSource)
- `spring-boot.md` says "No JPA/JDBC Auto-configuration" section was for a DynamoDB service — ignore entirely for this R2DBC + Flyway project

---

## Next Steps

The orchestrator should proceed to the **Gap Analysis** phase. The feature specification is complete and implementation-ready; the gap analysis should focus on:
1. Verifying all acceptance criteria are addressable with the identified file set
2. Confirming the Flyway+R2DBC dual-driver configuration approach
3. Identifying any missing infrastructure config (e.g., `maven-enforcer-plugin` banned dependencies, Lombok config file location)
4. Resolving the docker.md base image conflict explicitly before implementation starts
