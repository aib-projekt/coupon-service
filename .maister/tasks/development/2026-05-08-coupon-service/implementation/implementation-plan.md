# Implementation Plan — Coupon Service REST API

*10 task groups | 80 steps | Greenfield Java 25 Spring Boot 3 + WebFlux + R2DBC + Flyway*

---

## Standards Compliance

| Standard | Applies To |
|---|---|
| `.maister/docs/standards/backend/spring-boot.md` | All Java groups |
| `.maister/docs/standards/backend/build.md` | Group A (pom.xml) |
| `.maister/docs/standards/backend/api.md` | Group F (API layer) |
| `.maister/docs/standards/backend/naming-conventions.md` | All Java groups |
| `.maister/docs/standards/backend/migrations.md` | Group A (Flyway SQL) |
| `.maister/docs/standards/backend/logging.md` | Group F (GlobalExceptionHandler) |
| `.maister/docs/standards/backend/docker.md` | Group G (Docker) |
| `.maister/docs/standards/global/java-conventions.md` | All Java groups |
| `.maister/docs/standards/global/error-handling.md` | Groups D, F |
| `.maister/docs/standards/global/minimal-implementation.md` | All groups |
| `.maister/docs/standards/global/validation.md` | Groups D, F |
| `.maister/docs/standards/testing/jacoco.md` | Group A (pom.xml), Group I |
| `.maister/docs/standards/testing/junit-patterns.md` | Groups H, I |

---

## Group A — Project Scaffolding

**Dependencies**: None
**Purpose**: Maven POM, Flyway migrations, YAML config

### Steps

- [x] A.1 — Write failing smoke test: `CouponServiceApplicationTests.contextLoads()` (empty Spring Boot context load test in `src/test/java/pl/aibprojekt/couponservice/CouponServiceApplicationTests.java`); expect it fails because no pom.xml parent yet
- [x] A.2 — Rewrite `pom.xml`: Spring Boot 3.3.11 parent, groupId=`pl.aibprojekt`, artifactId=`coupon-service`, Java 25, UTF-8; all required dependencies and plugins; note: r2dbc driver is `org.postgresql:r2dbc-postgresql` (BOM-managed, not io.asyncer)
- [x] A.3 — Create `src/main/resources/db/migration/V1__create_coupons_table.sql`
- [x] A.4 — Create `src/main/resources/db/migration/V2__create_coupon_usages_table.sql`
- [x] A.5 — Create `src/main/resources/application.yml`
- [x] A.6 — Create `src/test/resources/application-test.yml`
- [x] A.7 — Create `src/main/java/pl/aibprojekt/couponservice/CouponServiceApplication.java`
- [x] A.8 — `mvn compile -P iCloud.nosync` → BUILD SUCCESS; all enforcer rules passed

---

## Group B — Domain Layer

**Dependencies**: Group A
**Purpose**: Domain records, error enum, exception

### Steps

- [x] B.1 — Write unit test `domain/CouponErrorCodeTest.java`: 7 assertions, 1 test, all pass
- [x] B.2 — Create `domain/CouponErrorCode.java`
- [x] B.3 — Create `domain/CouponException.java`
- [x] B.4 — Create `domain/Coupon.java`
- [x] B.5 — Create `domain/CouponUsage.java`
- [x] B.6 — `mvn test -Dtest=CouponErrorCodeTest -P iCloud.nosync` → 1 passed, BUILD SUCCESS

---

## Group C — Infrastructure Persistence Layer

**Dependencies**: Groups A, B
**Purpose**: R2DBC repositories, atomic UPDATE via DatabaseClient

### Steps

- [x] C.1 — Write Testcontainers-based `CouponRepositoryCustomImplTest.java` with 3 scenarios
- [x] C.2 — Create `CouponRepositoryCustom.java` interface
- [x] C.3 — Create `CouponRepositoryCustomImpl.java` with DatabaseClient atomic UPDATE
- [x] C.4 — Create `CouponRepository.java` (R2dbcRepository + CouponRepositoryCustom)
- [x] C.5 — Create `CouponUsageRepository.java` with existsByCouponCodeAndUserId
- [x] C.6 — 3 tests passed, BUILD SUCCESS; pom.xml updated: TC 1.21.4, testRelease=21

---

## Group D — Application Layer

**Dependencies**: Groups B, C
**Purpose**: Service interfaces, CouponServiceImpl with 7-step redemption flow

### Steps

- [x] D.1 — CouponServiceImplTest.java written (10 tests)
- [x] D.2 — GeoLocationService.java interface created
- [x] D.3 — CouponService.java interface created
- [x] D.4 — 5 DTO records created (CreateCouponRequest, RedeemCouponRequest, CouponResponse, RedemptionResponse, ErrorResponse)
- [x] D.5 — CouponServiceImpl.java created; key fix: Mono.defer() for lazy evaluation in .then() chains
- [x] D.6 — 11 tests passed (10 service + 1 existing), BUILD SUCCESS

---

## Group E — Infrastructure Geo + Config

**Dependencies**: Group D
**Purpose**: GeoProperties, IpAddressExtractor, IpApiGeoLocationService, config classes

### Steps

- [x] E.1 — IpApiGeoLocationServiceTest.java (4 tests with MockWebServer)
- [x] E.2 — IpAddressExtractorTest.java (4 tests)
- [x] E.3 — GeoProperties.java created
- [x] E.4 — IpAddressExtractor.java created
- [x] E.5 — IpApiGeoLocationService.java created
- [x] E.6 — GeoLocationConfig.java created
- [x] E.7 — WebClientConfig.java created
- [x] E.7b — ReactiveTransactionConfig.java created (TransactionalOperator bean)
- [x] E.8 — 8 tests passed (4 geo + 4 IP extractor), BUILD SUCCESS; added okhttp3:mockwebserver:4.12.0 test dep

---

## Group F — API Layer

**Dependencies**: Groups D, E
**Purpose**: Controller, GlobalExceptionHandler, validation, application entry point

### Steps

- [x] F.1 — CouponControllerTest.java (6 tests, inner @Configuration TestConfig workaround for Java 25/ASM)
- [x] F.2 — ValidCountryCode.java annotation created
- [x] F.3 — CountryCodeValidator.java using Locale.getISOCountries()
- [x] F.4 — GlobalExceptionHandler.java with dual-level logging
- [x] F.5 — CouponController.java created
- [x] F.6 — CouponServiceApplication.java updated with @OpenAPIDefinition; CreateCouponRequest updated with @ValidCountryCode
- [x] F.7 — 6 tests passed, BUILD SUCCESS

---

## Group G — Docker + Compose

**Dependencies**: Groups A, F
**Purpose**: Dockerfile and docker-compose.yml

### Steps

- [x] G.1 — Dockerfile created (eclipse-temurin:25-jre, spring:spring non-root user, actuator healthcheck)
- [x] G.2 — docker-compose.yml created (postgres:18 + app service with R2DBC/Flyway env vars)
- [x] G.3 — `docker compose config --quiet` → exit 0, no errors

---

## Group H — Unit Tests

**Dependencies**: Groups D, E, F
**Purpose**: Complete and verify all unit tests

### Steps

- [ ] H.1 — Review and complete `CouponServiceImplTest.java`: ensure all 10 test methods are present and passing with StepVerifier; verify Given/When/Then structure; verify null-safe argThat usage
- [ ] H.2 — Review and complete `IpApiGeoLocationServiceTest.java`: all 4 scenarios passing
- [ ] H.3 — Review and complete `IpAddressExtractorTest.java`: all extraction branches covered
- [ ] H.4 — Run all unit tests: `mvn test`; expect all GREEN, zero failures
- [ ] H.5 — Check JaCoCo partial coverage at unit-test level: `mvn verify -DskipITs`; note coverage numbers

---

## Group I — Integration + Concurrency Tests

**Dependencies**: All previous groups
**Purpose**: Full integration tests with Testcontainers PostgreSQL + concurrency tests

### Steps

- [ ] I.1 — Create `CouponControllerIT.java` base structure: @SpringBootTest(webEnvironment=RANDOM_PORT), @Testcontainers, @Container PostgreSQL, @DynamicPropertySource to inject r2dbc + flyway URLs, @MockBean GeoLocationService, WebTestClient
- [ ] I.2 — POST /api/v1/coupons integration tests (5 scenarios): should_create_201, should_return_409_duplicate, should_return_400_maxUses_zero, should_return_400_invalid_country, should_normalize_code_to_uppercase
- [ ] I.3 — GET /api/v1/coupons/{code} integration tests (3 scenarios): should_return_200, should_return_404, should_find_case_insensitive
- [ ] I.4 — POST /api/v1/coupons/{code}/redeem integration tests (8 scenarios): should_return_200_success, should_return_404_not_found, should_return_403_country_mismatch, should_return_503_geo_unavailable, should_return_409_exhausted, should_return_409_already_used_perUserLimit, should_allow_multiple_by_same_user_when_perUserLimit_false, should_return_400_missing_userId_when_perUserLimit_true
- [ ] I.5 — Concurrency test 1: N=20 parallel redemptions on coupon with maxUses=10; use CountDownLatch + Executors.newFixedThreadPool(20); assert exactly 10 successes and currentUses == 10 after
- [ ] I.6 — Concurrency test 2: N=10 concurrent same-user redemptions on perUserLimit=true coupon; assert exactly 1 success and 9 ALREADY_USED
- [ ] I.7 — Run integration tests: `mvn verify -Pfailsafe` or `mvn failsafe:integration-test`; expect all GREEN
- [ ] I.8 — Run full `mvn verify`; expect JaCoCo >= 80% instruction AND branch; zero test failures

---

## Group J — Review and Gap Analysis

**Dependencies**: All previous groups
**Purpose**: Final review, verify nothing was missed

### Steps

- [x] J.1 — Verify all files in spec.md "File Creation Order" exist on disk
- [x] J.2 — Run `mvn verify` one final time; confirm GREEN build with JaCoCo gate passed
- [x] J.3 — Verify `docker-compose up` can start (or at minimum `docker compose config` validates)
- [x] J.4 — Verify Swagger UI is reachable at /swagger-ui.html (start app locally or check config)
- [x] J.5 — Write final work-log summary entry

---

## Acceptance Criteria

- [x] `mvn verify` passes with zero test failures and JaCoCo >= 80% instruction + branch coverage
- [x] All 3 endpoints functional per spec
- [x] current_uses never exceeds max_uses under concurrent load (verified by Group I concurrency tests)
- [x] `docker compose up` starts service (Flyway migrations run, /actuator/health returns UP)
- [x] Swagger UI at /swagger-ui.html
