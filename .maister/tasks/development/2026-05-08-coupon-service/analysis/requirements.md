# Requirements — Coupon Service

*Phase 5 requirements gathering | Source of truth: feature-spec.md (7 approved sections)*

## Initial Description

Implement a production-ready REST API for coupon lifecycle management: POST /api/v1/coupons, GET /api/v1/coupons/{code}, POST /api/v1/coupons/{code}/redeem. Spring Boot 3 + WebFlux + R2DBC + Flyway + ip-api.com geolocation + atomic SQL concurrency + per-user enforcement. Java 25 + Maven.

## Q&A

| Question | Answer |
|---|---|
| Are there requirements not captured in feature-spec.md? | No — spec is complete, all 7 sections confirmed. |
| Should specification-creator reference external code patterns? | No — use .maister/docs/standards/ files only. |
| Rename artifactId from 'recruitment'? | Yes → coupon-service |
| Use Lombok? | No — Java records + explicit constructors only |
| Include SpringDoc OpenAPI? | Yes — springdoc-openapi-starter-webflux-ui |
| Include iCloud.nosync Maven profile? | Yes |

## Functional Requirements Summary

1. **Create coupon** — POST /api/v1/coupons; body: {code, maxUses, country, perUserLimit}; 201 on success; 409 COUPON_ALREADY_EXISTS on duplicate
2. **Get coupon** — GET /api/v1/coupons/{code}; 200 with full coupon state including currentUses; 404 COUPON_NOT_FOUND
3. **Redeem coupon** — POST /api/v1/coupons/{code}/redeem; atomic usage increment with 5 error codes: COUPON_NOT_FOUND(404), COUPON_EXHAUSTED(409), COUNTRY_NOT_ALLOWED(403), ALREADY_USED(409), GEO_UNAVAILABLE(503)
4. **Concurrency** — current_uses never exceeds max_uses under N concurrent requests
5. **Geolocation** — ip-api.com via X-Forwarded-For; fail closed on any error
6. **Per-user enforcement** — when perUserLimit=true: userId required, UNIQUE(coupon_code, user_id) enforced at DB level
7. **Code normalization** — uppercase at API boundary before any lookup or storage

## Technical Considerations

- Flyway needs JDBC DataSource (separate from R2DBC connection) — both drivers in pom.xml
- Reactive @Transactional covers only Steps 4+5 of redeem flow (atomic UPDATE + optional INSERT)
- DataIntegrityViolationException must be discriminated by constraint name
- Named constraint `uq_coupon_usages_per_user` in V2 migration
- IpAddressExtractor class filters private/loopback IPs from X-Forwarded-For chain
- GeoLocationConfig and WebClientConfig are separate @Configuration classes
- Minimal SpringDoc: only @OpenAPIDefinition at app level

## Reusability Opportunities

- Standards files: .maister/docs/standards/ (global, backend, testing conventions)
- No existing source code to reuse (greenfield)

## Scope Boundaries

**In**: pom.xml, all Java sources (~25 files), V1+V2 Flyway SQL, application.yml, application-test.yml, Dockerfile, docker-compose.yml, all test classes (unit + integration + concurrency), JaCoCo config

**Out**: Authentication, rate limiting, caching, admin endpoints, distributed tracing, metrics

## Visual Assets

None — REST API only, no UI.
