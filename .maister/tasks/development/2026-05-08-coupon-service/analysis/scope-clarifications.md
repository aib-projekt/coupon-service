# Scope Clarifications — Phase 2

*All 4 important decisions confirmed with recommended defaults.*

## Decisions Confirmed

| # | Decision | Choice |
|---|---|---|
| 1 | Migration constraint naming | Named: `CONSTRAINT uq_coupon_usages_per_user UNIQUE (coupon_code, user_id)` |
| 2 | IP extraction utility | Dedicated `IpAddressExtractor` class in `infrastructure/` |
| 3 | Geolocation config structure | Two classes: `GeoLocationConfig` + `WebClientConfig` |
| 4 | SpringDoc annotation depth | Minimal — `@OpenAPIDefinition` at app level only |

## Scope Boundaries Confirmed

**In scope**: Full greenfield implementation — pom.xml, all ~25 Java source files, Flyway SQL migrations, application.yml, Dockerfile, docker-compose.yml, unit tests, integration tests (Testcontainers), concurrency tests, JaCoCo config.

**Out of scope**: Authentication, rate limiting, caching, admin UI, distributed tracing.

## scope_expanded: false
