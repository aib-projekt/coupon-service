# Clarifications — Phase 1

*Resolved during codebase analysis*

## Q1: Artifact ID
**Question**: Should the pom.xml artifactId be renamed from 'recruitment'?
**Answer**: Yes — rename to `coupon-service`
**Impact**: artifactId = `coupon-service`, jarName = `coupon-service-1.0-SNAPSHOT.jar`

## Q2: Lombok
**Question**: Should we use Lombok for boilerplate reduction?
**Answer**: No — use Java records + explicit constructors only
**Impact**: Domain entities use `record` keyword. Services use standard Java. No `@Data`, `@Builder`, `@RequiredArgsConstructor`.

## Q3: SpringDoc OpenAPI
**Question**: Should we include Swagger UI / OpenAPI docs?
**Answer**: Yes — add `springdoc-openapi-starter-webflux-ui`
**Impact**: Adds `/swagger-ui.html` and `/v3/api-docs` endpoints. Useful for reviewers.

## Q4: iCloud.nosync Maven profile
**Question**: Should the iCloud.nosync macOS Maven profile be included?
**Answer**: Yes — add to pom.xml
**Impact**: Sets build directory to `target.nosync` on macOS to avoid iCloud sync conflicts.

## Standards Conflicts Resolved (from codebase analysis)

| Conflict | Resolution |
|---|---|
| `docker.md` references `eclipse-temurin:21-jre` | Use `eclipse-temurin:25-jre` (Java 25 project requirement) |
| `spring-boot.md` "No JPA/JDBC" rule (written for DynamoDB project) | NOT applied — Flyway requires JDBC DataSource; both JDBC + R2DBC coexist |
| Lombok standard in `lombok.md` | Not used — Java records + explicit constructors per Q2 answer |
