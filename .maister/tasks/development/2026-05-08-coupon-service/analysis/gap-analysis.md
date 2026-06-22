# Gap Analysis — Coupon Service

*Phase 2 output | Risk: medium*

## Current vs Desired State

| Area | Current State | Desired State | Gap |
|---|---|---|---|
| Maven POM | Bare skeleton, no deps, artifactId=recruitment | Spring Boot 3 parent, full dep set, artifactId=coupon-service | Full rewrite |
| Source files | None | ~25 Java files across 4 packages | Create all |
| DB migrations | None | V1 (coupons), V2 (coupon_usages) Flyway SQL | Create both |
| Config | None | application.yml + application-test.yml | Create both |
| Docker | None | Dockerfile + docker-compose.yml | Create both |
| Tests | None | Unit + integration + concurrency test classes | Create all |

## Task Characteristics

```yaml
has_reproducible_defect: false
modifies_existing_code: false
creates_new_entities: true
involves_data_operations: true
ui_heavy: false
```

## Risk Assessment

**Level: Medium**

| Risk | Severity | Mitigation |
|---|---|---|
| Flyway (JDBC) + R2DBC dual driver coexistence | High | Explicit `spring.flyway.url` with JDBC URL; separate from `spring.r2dbc.*` |
| Reactive `@Transactional` scope | High | Apply only to Steps 4+5 of redeem flow; geo call stays outside |
| `atomicIncrementUsage` row-count = 0 disambiguation | High | SELECT first (Step 3), then UPDATE; 0 rows post-SELECT → COUPON_EXHAUSTED |
| DataIntegrityViolationException discrimination | Medium | Named constraint `uq_coupon_usages_per_user`; match in GlobalExceptionHandler |
| Concurrency test reliability | Medium | Countdown latch pattern; fixed thread pool; assert ≤ maxUses |
| WebFlux bean validation | Low | Use `@Validated` on controller + `@Valid` on `@RequestBody`; wrap in `onErrorResume` |

## Decisions Needed

### Critical
*(none)*

### Important

1. **Constraint naming in migration SQL**
   - Option A: Named constraint `CONSTRAINT uq_coupon_usages_per_user UNIQUE (coupon_code, user_id)` ← **recommended**
   - Option B: Anonymous `UNIQUE (coupon_code, user_id)`
   - Rationale: Named constraint enables reliable discrimination in GlobalExceptionHandler

2. **IP extraction utility location**
   - Option A: Dedicated `IpAddressExtractor` class in `infrastructure/` ← **recommended**
   - Option B: Inline in controller
   - Rationale: X-Forwarded-For parsing involves private-IP filtering logic; keeps controller thin

3. **GeoLocation config vs WebClient config**
   - Option A: Two separate `@Configuration` classes (`GeoLocationConfig`, `WebClientConfig`) ← **recommended**
   - Option B: Single merged `InfrastructureConfig`
   - Rationale: Separation of concerns; easier to swap geolocation provider later

4. **SpringDoc OpenAPI annotation level**
   - Option A: Minimal — only `@OpenAPIDefinition` at app level; no per-endpoint `@Operation` ← **recommended**
   - Option B: Full — `@Operation`, `@ApiResponse`, `@Schema` on every endpoint and DTO
   - Rationale: Full annotations are high maintenance; Swagger UI auto-generates from WebFlux routes

## Scope Boundaries

**In scope**: pom.xml, all Java source files, Flyway SQL, application.yml, Dockerfile, docker-compose.yml, unit tests, integration tests (Testcontainers), concurrency tests, JaCoCo config

**Out of scope**: Authentication/authorization, rate limiting, caching layer, admin UI, metrics/tracing instrumentation
