## JUnit Patterns

### Given-When-Then Comment Structure
All test methods must use `// Given`, `// When`, `// Then` (or `// When & Then`) comment sections.

```java
@Test
void storeAuditEvent_shouldReturnCreated() {
    // Given
    when(service.store(dto)).thenReturn(response);

    // When
    var result = controller.storeIntent(dto);

    // Then
    assertEquals(HttpStatus.CREATED, result.getStatusCode());
}
```

### Test Method Naming: method_shouldBehavior
Name test methods as `methodUnderTest_shouldExpectedBehavior`.

**Examples**:
- `storeAuditEvent_shouldReturnResponse()`
- `getEventsByAggregateName_shouldReturnEmptyList()`
- `storeAuditEvent_shouldThrowWhenEntityIdMissing()`

### @BeforeEach setUp for Shared Fixtures
Initialize shared test fixtures (valid DTOs, entity builders, common mocked responses) in a `@BeforeEach setUp()` method. Do not repeat initialization across test methods.

### MockitoExtension for Unit Tests
Use `@ExtendWith(MockitoExtension.class)` with `@Mock` and `@InjectMocks` for unit tests. Use `@WebMvcTest` + `@MockBean` for controller slice tests.

```java
@ExtendWith(MockitoExtension.class)
class AuditStorageServiceTest {
    @Mock
    private DynamoDbAuditRepository repository;

    @InjectMocks
    private AuditStorageService service;
}
```

### argThat Null-Safety in Mockito
When multiple `when(mock.method(argThat(lambda))).thenReturn(...)` stubs are registered on the same mock method, Mockito evaluates all registered matchers against every actual invocation — including calling each lambda with `null` as a candidate argument during matcher disambiguation. Without a null guard, this causes `NullPointerException` inside the lambda.

Always prefix argThat lambda bodies with `p != null &&`:

```java
// Correct — null-safe
when(repo.listKeysByPrefix(argThat(p -> p != null && p.startsWith("tenant-1/2025/06/01/"))))
    .thenReturn(List.of("key1"));

// Wrong — throws NullPointerException when multiple stubs are registered
when(repo.listKeysByPrefix(argThat(p -> p.startsWith("tenant-1/"))))
    .thenReturn(List.of("key1"));
```

Apply this guard to every `argThat` predicate in a test class, not just the one that triggers multiple stubs — a future test adding a second stub could break the existing one.

### Unit vs Integration Test Class Naming
Surefire (unit) excludes `*IT.java` and `*IntegrationTest.java`. Failsafe (integration) includes only `*IT.java`. The suffix is the contract that routes a test class to the correct Maven plugin:
- `FooTest.java` → unit test, runs on `mvn test`
- `FooIT.java` → integration test, runs on `mvn verify`

Never mix concerns in one class. A class named `FooIT` must require an external resource (database, container, HTTP service).

### Integration Tests — @SpringBootTest + @Testcontainers + @ServiceConnection
Full integration tests use this three-annotation stack:
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CouponServiceIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");
}
```
`@ServiceConnection` auto-configures both R2DBC and Flyway URLs from the container — no manual `@DynamicPropertySource` needed. Use `@MockBean` to isolate external HTTP services (e.g., geo API) within integration tests.

### StepVerifier for Reactive Stream Assertions
Test `Mono` and `Flux` values using `StepVerifier` from `reactor-test`. Never block with `.block()` in tests.
```java
// Correct
StepVerifier.create(service.getCoupon("CODE"))
    .assertNext(response -> assertThat(response.code()).isEqualTo("CODE"))
    .verifyComplete();

// Error path
StepVerifier.create(service.getCoupon("MISSING"))
    .expectErrorMatches(e -> e instanceof CouponException &&
        ((CouponException) e).getErrorCode() == CouponErrorCode.COUPON_NOT_FOUND)
    .verify();

// Avoid
service.getCoupon("CODE").block(); // blocks the test thread, hides backpressure issues
```

### WebTestClient for HTTP Layer Tests
Controller slice tests (`@WebFluxTest`) use `WebTestClient`. `MockMvc` is absent from the project — do not introduce it.
```java
webTestClient.get().uri("/api/v1/coupons/{code}", "SPRING20")
    .exchange()
    .expectStatus().isOk()
    .expectBody()
    .jsonPath("$.code").isEqualTo("SPRING20");
```

### AssertJ assertThat Exclusively
All assertions use AssertJ `assertThat()`. JUnit `assertEquals` / `assertTrue` / `assertNull` are not used. Import: `import static org.assertj.core.api.Assertions.assertThat;`

### JVM argLine for Mockito on Java 25
Both Surefire (unit) and Failsafe (integration) require JVM arguments for Mockito inline mock maker to instrument `java.base` module classes on Java 25. Configure via `@{argLine}` (JaCoCo agent prepend placeholder):
```xml
<argLine>@{argLine} -XX:+EnableDynamicAgentLoading
  --add-opens java.base/java.lang=ALL-UNNAMED
  --add-opens java.base/java.util=ALL-UNNAMED</argLine>
```
Do not remove `@{argLine}` — without it JaCoCo instrumentation is skipped and coverage will be zero.

### Test Compilation Target Java 21
Set `maven.compiler.testRelease=21` in `pom.xml` properties. Spring Boot 3.3.x ASM (bundled in spring-core 6.1.x) cannot parse Java 25 class file version (69) during `@DataR2dbcTest` and `@WebFluxTest` context loading. Main sources continue targeting Java 25; only test sources are downgraded to Java 21 bytecode.
