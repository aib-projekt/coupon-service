## Docker Standards

### Non-Root Docker User
Docker containers must run as a non-root user. Define `spring:spring` (UID 10001) and switch with `USER spring:spring`.

### Debian-Based Docker Image
Use `eclipse-temurin:21-jre` (Debian-based) as the Docker base image, not Alpine variants. Provides better debugging tools and broader library compatibility.

### Docker Health Check via Actuator
Configure Docker HEALTHCHECK using the Spring Boot Actuator health endpoint:
```dockerfile
HEALTHCHECK CMD curl -f http://localhost:8080/actuator/health || exit 1
```

### curl Not Available in eclipse-temurin JRE
The `eclipse-temurin` JRE image is minimal and does not include `curl` or `wget`. Install `curl` via `apt-get` before switching to the non-root user, otherwise Docker HEALTHCHECK will fail with "command not found":
```dockerfile
FROM eclipse-temurin:25-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN addgroup --system spring && adduser --system --ingroup spring spring
USER spring:spring
```
Always clean apt cache in the same `RUN` layer to keep image size minimal.

### R2DBC and JDBC Require Separate URL Schemes
Spring Data R2DBC and Flyway use different URL schemes and must always be configured as separate environment variables. Using the same JDBC URL for both causes startup failure (`URL does not start with the r2dbc scheme`):
- `SPRING_R2DBC_URL` → `r2dbc:postgresql://host:5432/dbname`
- `SPRING_FLYWAY_URL` → `jdbc:postgresql://host:5432/dbname`

In `.env`, define a single base URL without scheme prefix and add the scheme in `docker-compose.yml`:
```env
DATABASE_URL=postgresql://postgres:5432/couponservice
```
```yaml
environment:
  SPRING_R2DBC_URL: r2dbc:${DATABASE_URL}
  SPRING_FLYWAY_URL: jdbc:${DATABASE_URL}
```
