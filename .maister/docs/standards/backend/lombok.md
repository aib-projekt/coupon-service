## Lombok Standards

### Lombok with @Generated Annotation
Use Lombok in `provided` scope. Enable `addLombokGeneratedAnnotation=true` in `lombok.config` so all Lombok-generated code is excluded from JaCoCo coverage measurement.

`lombok.config` must contain:
```
lombok.addLombokGeneratedAnnotation=true
```

### @Slf4j for Logging
All beans must use the `@Slf4j` Lombok annotation for logging. Do not instantiate Logger manually.

**Preferred**: `@Slf4j` on class, then `log.info(...)`
**Avoid**: `private static final Logger log = LoggerFactory.getLogger(Foo.class)`

### @RequiredArgsConstructor for Dependency Injection
Use Lombok `@RequiredArgsConstructor` with `private final` fields for constructor-based dependency injection. Never use `@Autowired` field injection.

**Preferred**: `@RequiredArgsConstructor` + `private final AuditStorageService service`
**Avoid**: `@Autowired AuditStorageService service`

### @Data @Builder on Domain Entities
DynamoDB entity classes use the full Lombok annotation stack: `@Data @Builder @NoArgsConstructor @AllArgsConstructor`.
