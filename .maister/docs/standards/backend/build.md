## Build Standards

### Maven 3.8+ Required
Project requires Maven 3.8.0 or higher. Enforced by `maven-enforcer-plugin` — build halts if requirement is not met.

### Maven iCloud.nosync Profile on macOS
On macOS, ALWAYS use the `-P iCloud.nosync` Maven profile to exclude the `target` folder from iCloud sync. Not required in CI/CD (Linux) or Windows.

```bash
mvn clean compile -P iCloud.nosync
mvn clean test -P iCloud.nosync
mvn clean package -P iCloud.nosync
mvn clean install -P iCloud.nosync
```

### BOM-Managed Dependency Versions
Manage all dependency versions via BOMs and Maven `<properties>`, not per-dependency version tags. When a BOM covers a dependency, omit the `<version>` tag.

**Preferred**:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```
**Avoid**: `<version>3.3.10</version>` inline on BOM-managed dependencies.

### CI Pipeline Stage Order
GitLab CI pipeline follows mandatory stage order: `build → test → package → docker → deploy`. No stage bypassing.

### Production Deploy Off by Default
Production deployment is disabled by default (`PRODUCTION_DEPLOY: false`, `DRY_RUN: true`). Explicit promotion to production is required.

### delegate-change-service — Local Test Service Only
`local-docker/delegate-change-service/` is a local E2E test service. It is NOT part of the main `audit-storage` build (separate `pom.xml` with its own Spring Boot parent). Build it independently only when running the local Docker Compose stack:

```bash
cd local-docker/delegate-change-service && mvn clean package -P iCloud.nosync
```
