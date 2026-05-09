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
