## Audit Patterns

### Audit Method Naming Convention
Audit methods must follow the naming pattern: `auditCreate`, `auditUpdate`, `auditDelete`.

### Audit Methods Must Not Throw Exceptions
Methods prefixed with `audit*` must NEVER throw exceptions — only log errors. Audit operations must never interrupt the calling business operation.

```java
// Correct
try {
    auditService.auditCreate(entity);
} catch (Exception e) {
    log.error("Audit failed: {}", rootCauseMessage(e)); // log, don't re-throw
}
```

### Sensitive Field Masking
Sensitive fields must be annotated with `@SensitiveField` to ensure they are masked in audit logs and event payloads.

### Fields Excluded from Audit
Fields to be omitted entirely from audit must be annotated with `@IgnoreAuditField`.

### changedBy Must Be String
The `changedBy` field in audit events must be a `String` (user identifier), not a complex object.

### Required Audit Event Fields
Validate these fields before publishing audit events: `aggregateName`, `entityId`, `operation`, `timestamp`. The service returns 400 Bad Request for missing required fields. Publishers must validate before sending; the storage service validates as a safety net.

### Entity ID Source
Always extract the entity ID from the field annotated with `@Id` (JPA). Do not use other fields as the entity identifier.

### Transaction Propagation for Audit Events
- `INTENT_TO_CHANGE` → use `REQUIRES_NEW` propagation: isolated transaction that survives a business operation rollback
- `CHANGE_EXECUTED` → use `REQUIRED` propagation: atomic with the business data modification

### Dual-Write Pattern
1. Write `INTENT_TO_CHANGE` BEFORE the business operation in an isolated transaction (`REQUIRES_NEW`)
2. Write `CHANGE_EXECUTED` AFTER the operation in the same transaction (`REQUIRED`)
3. Both events share the same `eventId` (automatically reused between the pair)

### Event Schema Backward Compatibility
Event schema changes must be backward compatible. Introduce new optional fields; never remove or rename existing fields.

### SQS Consumer Exception Re-throw for DLQ Routing
On SQS processing failure, the consumer must re-throw the exception. SQS will redeliver according to visibility timeout and route to the dead-letter queue after max attempts.

### ApplicationException Base Class
Use abstract `ApplicationException` (extends `RuntimeException`) with a `String code` field as the base for all custom business exceptions. The `LoggingAspect` logs `ApplicationException` at WARN level vs ERROR for generic exceptions.

### AuditContext ThreadLocal
`AuditContext` stores the current `eventId` in a `ThreadLocal` variable, enabling automatic reuse of the same `eventId` between `INTENT_TO_CHANGE` and `CHANGE_EXECUTED` events in the dual-write pattern. Always clean the ThreadLocal after the operation completes to prevent context leakage.

### Event Lifecycle States
Audit events progress through the following states:

```
DRAFT → NEW → IN_PROGRESS → DONE
                          ↘ ERROR
```

- `DRAFT` — event created but business transaction not yet committed (eventual-consistency mode only)
- `NEW` — event confirmed (transaction committed) or created directly (outbox mode)
- `IN_PROGRESS` — publisher is actively processing the event
- `DONE` — event successfully published
- `ERROR` — publishing failed after all retry attempts

In eventual-consistency mode, `AuditAspect` detects transaction commit/rollback and transitions `DRAFT → NEW` (commit) or `DRAFT → ERROR` (rollback).

### Audit Operation Modes
Two modes are supported, configured via `audit.mode` in `application.yml`:

| Mode | Value | Behavior |
|---|---|---|
| Outbox (default) | `outbox` | Events staged in PostgreSQL AUDIT_OUTBOX table, published asynchronously |
| Eventual Consistency | `eventual-consistency` | Events stored in `InMemoryAuditOutboxRepository`, no DB write required |

Default: `outbox`. The `eventual-consistency` mode enables audit without a database write by holding events in memory.

### InMemoryAuditOutboxRepository
Used only in `eventual-consistency` mode. Bounded capacity: default 10,000 events. When the limit is reached, oldest events are evicted (FIFO) to prevent out-of-memory issues. `DONE` and `ERROR` events are cleaned up every 10 minutes; `ERROR` events are logged before deletion.

### Progressive Retry for Event Publishing
On publish failure, retries are scheduled with progressive backoff:

Default thresholds: `[30s, 10m, 20m, 30m, 2h]`

```
nextAttemptTime = currentTime + backoff[attemptCount]
```

When `audit.retry.maxAttempts` exceeds the number of thresholds, the last threshold (`2h`) is reused for all remaining attempts. After all attempts are exhausted, the event is marked `ERROR` and logged.

Configurable via `application.yml`:
```yaml
audit:
  retry:
    maxAttempts: 5           # default: number of backoff thresholds
    backoff: [30s, 10m, 20m, 30m, 2h]  # default thresholds
```
