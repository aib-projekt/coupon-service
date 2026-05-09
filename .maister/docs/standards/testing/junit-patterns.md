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
