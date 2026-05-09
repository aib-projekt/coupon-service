## Spring Boot Standards

### Spring Boot BOM Version Management
Use Spring Boot 3.3.x and Spring Cloud 2023.0.x BOMs for dependency management. Do not hardcode individual Spring dependency versions inline — use the BOM.

**Preferred**: `<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>`
**Avoid**: `<version>3.3.10</version>` on each Spring dependency

### Spring Profile Naming Convention
Use four standard Spring profiles: `local` (local development), `det` (development environment), `iut` (integration/UAT), `prod` (production). Profile files: `application-{local|det|iut|prod}.yml`.

### Actuator Health Always Exposed
Spring Boot Actuator `/actuator/health` must always be exposed with `show-details: always`. This endpoint is used for Docker health probes.

### Jackson ISO-8601 Date Serialization
Configure Jackson to serialize dates as ISO-8601 strings, not timestamps. Enable JavaTimeModule for Java 8+ date/time types.

```yaml
spring:
  jackson:
    serialization:
      write-dates-as-timestamps: false
```

**Output**: `"2025-04-20T10:30:00Z"` not `1745144200000`

### No JPA/JDBC Auto-configuration
Explicitly exclude JPA and JDBC DataSource auto-configuration. This is a DynamoDB-only service.

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
      - org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
```

### Config via Environment Variables
All environment-specific configuration must use `${VAR_NAME}` or `${VAR_NAME:default}` placeholders in YAML. No hardcoded values or secrets.

**Preferred**: `endpoint: ${DYNAMODB_ENDPOINT:http://localhost:8000}`
**Avoid**: `endpoint: http://real-aws.amazonaws.com`
