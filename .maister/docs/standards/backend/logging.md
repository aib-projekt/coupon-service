## Logging Standards

### Dual-Level Exception Logging
Log exceptions at two levels: ERROR with root cause message summary (production-safe), DEBUG with full stacktrace (diagnostic).

```java
log.error("Failed to store audit event: {}", rootCauseMessage(e));
log.debug("Full stacktrace for failed audit event store:", e);
```

Use `static import` of `ExceptionDescription.rootCauseMessage`.

### Logging Level Conventions
- `DEBUG` — operation details, method parameters, internal state
- `WARN` — publication failures, recoverable issues
- `ERROR` — audit process errors (but NEVER interrupt the business operation)

### MDC Context Enrichment
Enrich MDC with `businessOperation`, `duration`, and `errorType` keys on every service call (via LoggingAspect). Always clear MDC keys in `finally` block to prevent context leakage.

### Structured Log Console Pattern
Use this pattern for console logging:
```
%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n
```

### AOP Pointcut Expressions Require Manual Updates on Package Renames
IDE refactoring (rename/move) does NOT update AOP pointcut strings — they are plain string literals. When any package under `pl.aibprojekt.audit.storage` is renamed or moved, manually verify and update the two pointcut expressions in `shared/aspect/LoggingAspect.java`:

- `@AfterReturning` pointcut — references `shared.exception.ExceptionHandlerControllerAdvice` by fully-qualified name
- `@Around` pointcut — references each feature package (`audit.*.*`, `archiving.*.*`, `restore.*.*`) by pattern

After any structural package change, search for hardcoded package paths in `LoggingAspect.java` before running tests.
