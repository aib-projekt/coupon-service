## Naming Conventions

### PascalCase Class Names with Layer Suffix
All classes use PascalCase with a descriptive layer suffix indicating their role.

| Layer | Suffix | Example |
|---|---|---|
| HTTP handler | Controller | RestAuditController |
| Business logic | Service | AuditStorageService |
| Data access | Repository | DynamoDbAuditRepository |
| Message handler | Consumer | SqsAuditConsumer |
| Cross-cutting | Aspect | LoggingAspect |
| DTO conversion | Mapper | AuditMapper |
| Spring config | Config | DynamoDbConfig |

### Feature-First Package Structure
Organize code into feature packages under `pl.aibprojekt.{module}.{service}`, following domain-driven design (DDD):
- `{feature}` — one package per business subdomain (e.g., `audit`, `archiving`, `restore`)
- `shared.model` — domain entities shared across features (Shared Kernel)
- `shared.repository` — infrastructure repositories shared across features
- `shared.config` — Spring configuration shared across features
- `shared.aspect` — AOP cross-cutting concerns
- `shared.exception` — custom exceptions
- `shared.dto` — shared data transfer objects
- `shared.constant` — constants

### Package Naming Convention
Use `pl.aibprojekt.{module}.{feature}` pattern.

**Examples**: `pl.aibprojekt.audit.storage.audit`, `pl.aibprojekt.audit.storage.shared.repository`

### Service Class Naming
Service classes follow the `AuditFeatureService` pattern.

**Examples**: `AuditEventService`, `AuditStorageService`

### Repository Interface Naming
Repository interfaces follow the `AuditFeatureRepository` pattern.

**Examples**: `DynamoDbAuditRepository`, `AuditOutboxRepository`

### DTO Suffix Convention
All Data Transfer Objects must use the `Dto` suffix.

**Examples**: `AuditEventDto`, `IntentToChangeDto`

### Prefer Java Records for Simple DTOs
Use Java records for simple, immutable response DTOs. Records provide immutability and auto-generated equals/hashCode/toString.

```java
public record AuditResponse(String eventId, String aggregateName, String eventType) { }
```

### Use Optional for Nullable Values
Use `Optional<T>` for return values that may be absent. Do not return null from service methods.
