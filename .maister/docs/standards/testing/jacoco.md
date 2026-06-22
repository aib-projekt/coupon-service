## JaCoCo Coverage Standards

### 80% Coverage Gate — Mandatory
Minimum 80% instruction AND branch coverage required. Build halts on failure (`haltOnFailure: true`). Enforced by `jacoco-maven-plugin` check goal and `JACOCO_MINIMUM_COVERAGE: "0.80"` in GitLab CI.

### Coverage Scope — Packages That Must Be Tested
The 80% gate applies to these packages: `service`, `consumer`, `controller`, `mapper`, `aspect`, `exception`, `repository`. All business logic and I/O handling must be covered.

Explicitly excluded from the gate: `model`, `dto`, `config`, Lombok-generated code, `Application` main class.

### JaCoCo Coverage Exclusions
The following are excluded from coverage measurement:

| Pattern | What It Excludes |
|---|---|
| `**/*$*` | Lombok-generated inner classes |
| `**/model/**` | Anemic domain entities |
| `**/*Dto.class` | Data Transfer Objects |
| `**/*DTO.class` | Data Transfer Objects (uppercase variant) |
| `**/config/**Configuration.class` | Spring configuration classes |
| `**/*AutoConfiguration.class` | Spring auto-configuration |
| `**/*Application.class` | Spring Boot application entry point |

Lombok-generated methods are additionally excluded via `@Generated` (set by `lombok.addLombokGeneratedAnnotation=true`).

### Spring Boot Test Starter Only
Use `spring-boot-starter-test` as the sole test dependency. It provides JUnit 5, Mockito, and AssertJ. Do not add redundant test libraries.

### Verifying the Coverage Report Locally
After running tests, open the HTML report to verify coverage:

```bash
mvn clean test -P iCloud.nosync
open target.nosync/site/jacoco/index.html
```

Expected: `model` and `config` packages do **not** appear in the report. `service`, `controller`, and `repository` packages show >= 80%.
