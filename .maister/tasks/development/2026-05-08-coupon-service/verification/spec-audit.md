# Specification Audit — Coupon Service REST API

**Audited file**: `.maister/tasks/development/2026-05-08-coupon-service/implementation/spec.md`
**Ground-truth sources**:
- `product-design/…/analysis/feature-spec.md`
- `product-design/…/analysis/design-decisions.md`
- `product-design/…/outputs/product-brief.md`
- `development/…/analysis/clarifications.md`
- `development/…/analysis/gap-analysis.md`
- `.maister/docs/standards/` (all relevant files)

**Date**: 2026-05-08
**Auditor**: spec-auditor agent

---

## Compliance Status

**MOSTLY COMPLIANT** — the spec is thorough and implementable with two medium-severity gaps and several
low-severity ambiguities that an implementer would likely work around correctly, but that create
unnecessary guesswork or silent divergence from the ground-truth design.

No critical issues are present. No high-severity gaps. Two medium-severity gaps are present.

---

## Summary

The spec is the best single document an implementer could receive for this service. Every endpoint is
fully specified, all 7 error codes are present with correct HTTP status codes, the data model is
complete, the 7-step redeem flow is ordered and annotated, the reactive transaction scope is stated,
and the critical invariants (uppercase normalization, geo-before-UPDATE, interface boundary) are all
explicit. The concurrency test specification directly addresses the recruitment reviewer's primary
concern.

The two medium gaps are:

1. The `remainingUses` calculation formula in Step 7 is subtly wrong relative to what the atomic
   UPDATE actually produces, and the spec does not say how to obtain the post-update count.
2. The `GlobalExceptionHandler` discrimination rule for `DataIntegrityViolationException` →
   `COUPON_ALREADY_EXISTS` names a constraint (`coupons_pkey`) that does not exist anywhere in the
   migration SQL and is not in the feature-spec.

The low-severity issues are editorial and implementation-style concerns that could trip up a
developer reading the spec cold.

---

## Critical Issues

None.

---

## Medium-Severity Gaps

### M1 — `remainingUses` formula is ambiguous and potentially wrong

**Spec reference**: Step 7, spec.md line 275:
```
Return 200 OK: {code, remainingUses = maxUses - (currentUses + 1), redeemedAt}
```

**Evidence**: The atomic UPDATE (`UPDATE coupons SET current_uses = current_uses + 1 WHERE …`) has
already incremented `current_uses` in the database by the time Step 7 runs. The `Coupon` record
fetched at Step 3 holds the *pre-update* `currentUses`. Therefore:

- `maxUses - (currentUses + 1)` where `currentUses` is the Step 3 snapshot is correct math,
  but the spec does not say so explicitly. A developer who re-reads the coupon from the database
  after the UPDATE will get `currentUses` already incremented; applying `(currentUses + 1)` again
  would undercount remaining uses by 1.
- The feature-spec (line 267) uses `remainingUses = maxUses - currentUses` (post-update) — a
  different formula. Both are equivalent only if the reader knows which `currentUses` value to use.

**Gap description**: The spec gives a formula but does not specify which snapshot of `currentUses`
it applies to (Step 3 value vs. post-UPDATE re-read). The two sources give different-looking
formulas for the same semantic result. An implementer who re-fetches the coupon after the UPDATE
and then applies the spec's formula will produce off-by-one errors.

**Category**: Incomplete

**Severity**: Medium — the success criterion (spec.md line 738:
"currentUses field in GET response reflects the correct post-redemption count") confirms correctness
matters, and the concurrency test (N=20 on maxUses=10) would expose systematic off-by-one errors
in `remainingUses`.

**Recommendation**: Explicitly state which value of `currentUses` the formula uses. The clearest
wording: "Use the `currentUses` value from the Step 3 SELECT (pre-update snapshot);
`remainingUses = maxUses - currentUses - 1` where `currentUses` is the value read in Step 3."
Alternatively: "After the UPDATE, `remainingUses = maxUses - newCurrentUses`
where `newCurrentUses` is obtained from atomicIncrementUsage returning the new count" (which would
require the custom repository method signature to return the updated count, not the row count).

---

### M2 — `COUPON_ALREADY_EXISTS` exception discrimination names a constraint that does not exist

**Spec reference**: spec.md lines 351–352 (GlobalExceptionHandler section):
```
DataIntegrityViolationException containing coupon PK violation → 409 COUPON_ALREADY_EXISTS
Discrimination … by checking for the coupons_pkey constraint name
```

**Evidence**:
- Migration V1 (spec.md lines 121–130): `code VARCHAR(64) PRIMARY KEY` — no named PRIMARY KEY
  constraint. PostgreSQL auto-names primary key constraints as `{tablename}_pkey`, so the
  actual constraint name is `coupons_pkey`. However, this auto-generated name appears **nowhere**
  in the spec's migration SQL, and the spec relies on checking `.getMessage().contains("coupons_pkey")`.
- The feature-spec (analysis/feature-spec.md, line 393) gives the same `DataIntegrityViolationException`
  handling but does not specify the constraint name string to match.
- The gap-analysis (gap-analysis.md, line 49) specifies and names only the usage constraint
  (`uq_coupon_usages_per_user`), not the PK constraint name.

**Gap description**: The spec instructs the implementer to check for the string `"coupons_pkey"`
in the exception message, but:
(a) This constraint name is PostgreSQL's implicit convention, not an explicitly named constraint in
the migration DDL. The spec states it as fact but does not explain why or how to verify the
exact string PostgreSQL will emit.
(b) The exact error message format Spring's `DataIntegrityViolationException` wraps around the
PostgreSQL error varies by driver version and wrapper depth. Relying on `.contains("coupons_pkey")`
will work on PostgreSQL but the spec presents it without qualification.
(c) The feature-spec does not document this string at all, making it an addition in the impl spec
without source traceability.

**Category**: Incomplete

**Severity**: Medium — if the implementer uses a different matching strategy (e.g., catching any
`DataIntegrityViolationException` that does not contain `uq_coupon_usages_per_user`) the behavior
is functionally correct but the spec's stated approach is underdocumented. If the string matching
is wrong, duplicate code creation silently falls through to the catch-all 500 handler.

**Recommendation**: Either: (a) explicitly add `CONSTRAINT coupons_pkey PRIMARY KEY` to the V1
migration DDL so the constraint name is defined in the spec itself, or (b) specify that the PK
exception is caught by checking that the `DataIntegrityViolationException` message does NOT
contain `uq_coupon_usages_per_user` (complementary match) since all other `DataIntegrityViolation`
exceptions from the coupons table will be PK violations. Option (b) is more robust.

---

## Low-Severity Gaps

### L1 — `@Transactional` placement instruction is internally inconsistent

**Spec reference**: spec.md line 582 (Reactive Transaction Scope section):
> "`@Transactional` annotation on `CouponServiceImpl.redeemCoupon()` covers only Steps 6a and 6b"

**Also**: spec.md lines 270–271 (Redemption Flow table, header row):
> "Steps 1–5 execute OUTSIDE any database transaction"

**Gap**: The spec says `@Transactional` is on `redeemCoupon()`, which is the whole method. In
reactive Spring, `@Transactional` on a method returning `Mono<T>` begins the transaction when
the Mono is subscribed and ends it on completion — this means the transaction wraps the *entire*
reactive chain unless the implementation explicitly defers the transactional segment using
`TransactionalOperator`. The spec acknowledges this implicitly ("Use `transactionalOperator.transactional(…)`
or reactive `@Transactional`") but does not resolve the contradiction between annotating the
whole method and the stated intent to scope the transaction to Steps 6a/6b only.

**Category**: Ambiguous

**Severity**: Low — the recommended resolution (`transactionalOperator.transactional(…)`) is
correct and stated, but a developer who applies `@Transactional` on `redeemCoupon()` naively
will include the geo call inside the transaction, violating invariant 2 (geo before UPDATE,
not inside transaction).

**Recommendation**: Remove the statement that `@Transactional` is on `redeemCoupon()`. Prescribe
`TransactionalOperator` explicitly and show the chain structure:
`geoMono.then(perUserCheckMono).then(transactionalOperator.transactional(atomicUpdateMono))`.

---

### L2 — DTO naming conflicts with the `naming-conventions.md` standard (`Dto` suffix)

**Spec reference**: spec.md lines 394–416 (DTO Definitions), naming-conventions.md:
> "All Data Transfer Objects must use the `Dto` suffix. Examples: `AuditEventDto`, `IntentToChangeDto`"

**Evidence**:
- spec.md specifies: `CreateCouponRequest`, `RedeemCouponRequest`, `CouponResponse`,
  `RedemptionResponse`, `ErrorResponse` — none use the `Dto` suffix.
- The spec explicitly says "No Lombok" (clarifications.md Q2) and "Java records" — consistent
  with the naming-conventions.md preference for records. The suffix conflict is not addressed.

**Gap**: The spec mentions the naming-conventions standard in its Standards Compliance table but
does not explain the deliberate deviation from the `Dto` suffix rule for DTOs.

**Category**: Extra (spec diverges from a standard without documenting the decision)

**Severity**: Low — the deviation is pragmatic (`Request`/`Response` suffixes are widely accepted
in Spring projects and clearer in context), but the spec's Standards Conflicts Resolved section
does not list this deviation, which means a reviewer checking standards compliance would flag it.

**Recommendation**: Add one line to the Standards Conflicts Resolved table:
"naming-conventions.md `Dto` suffix rule — Not applied; `Request`/`Response` suffixes used
instead for clarity in a request/response-oriented API."

---

### L3 — `CouponUsageRepository` in feature-spec includes `findByCouponCodeAndUserId` which is absent from impl spec

**Spec reference**: spec.md line 386 (`CouponUsageRepository` interface signature):
```java
Mono<Boolean> existsByCouponCodeAndUserId(String couponCode, String userId);
```

**Feature-spec reference**: feature-spec.md lines 97–99:
```java
Mono<Boolean> existsByCouponCodeAndUserId(String couponCode, String userId);
Mono<CouponUsage> findByCouponCodeAndUserId(String couponCode, String userId);
```

**Gap**: The feature-spec includes `findByCouponCodeAndUserId` in the `CouponUsageRepository`
interface; the impl spec omits it. This is not a functional gap (Step 5b only needs `exists`)
but it means the impl spec stripped a method that was in the approved design. An implementer
following the impl spec alone will not notice. However, a reviewer cross-checking the feature-spec
against the implementation would see the method absent.

**Category**: Missing (relative to feature-spec)

**Severity**: Low — `findByCouponCodeAndUserId` has no caller in the specified flow; omitting it
is consistent with the minimal-implementation standard. The omission is correct per
`global/minimal-implementation.md` ("every method must serve a purpose"). This is a case where
the impl spec correctly diverges from the feature-spec.

**Recommendation**: No change needed; this is a correct optimization. Optionally add a note:
"findByCouponCodeAndUserId omitted — no caller in specified flow (per minimal-implementation.md)."

---

### L4 — Dockerfile in spec adds non-root user setup but references a different COPY path than feature-spec

**Spec reference**: spec.md lines 509–516 (Dockerfile):
```dockerfile
FROM eclipse-temurin:25-jre
RUN addgroup --system spring && adduser --system spring --ingroup spring
USER spring:spring
COPY target/coupon-service-1.0-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

**Feature-spec reference**: feature-spec.md lines 632–637:
```dockerfile
FROM eclipse-temurin:25-jre
COPY target/*.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

**Evidence**:
- The impl spec Dockerfile: COPY to `app.jar` (root), ENTRYPOINT references `/app.jar`
- The feature-spec Dockerfile: COPY to `/app/app.jar`, ENTRYPOINT references `/app/app.jar`
- The impl spec adds the non-root user (`spring:spring`) which the feature-spec omits — this is
  correct per docker.md standard
- The COPY path and hardcoded JAR name in the impl spec differ from the feature-spec wildcard

**Gap**: The impl spec hardcodes `coupon-service-1.0-SNAPSHOT.jar` as the COPY source.
If the pom.xml uses the default Maven artifact naming, this is `coupon-service-1.0-SNAPSHOT.jar`
— consistent. However, if the version changes, the Dockerfile breaks. The feature-spec uses a
wildcard (`target/*.jar`) which is more robust. The ENTRYPOINT path is also inconsistent
(`/app.jar` in impl spec vs. `/app/app.jar` in feature-spec) — the COPY and ENTRYPOINT in the
impl spec are consistent with each other, but differ from the feature-spec.

**Category**: Inconsistent (between impl spec and feature-spec; impl spec is internally consistent)

**Severity**: Low — functionally both work if the JAR name matches; the wildcard form is
preferable. The non-root user addition is correct and an improvement over the feature-spec.

**Recommendation**: Use the wildcard form from the feature-spec for the COPY instruction
(`COPY target/*.jar app.jar`). Keep the non-root user setup from the impl spec.

---

### L5 — `application-test.yml` content not specified

**Spec reference**: spec.md lines 500–503:
> "Testcontainers dynamically provides the database URLs. The test configuration overrides R2DBC
> and Flyway URLs to point to the Testcontainers-managed PostgreSQL instance."

**Gap**: The spec lists `application-test.yml` as a required file (in File Creation Order, step 4
via implication, and in the Components section) but does not provide the actual YAML content or
the Testcontainers auto-configuration mechanism. Testcontainers can be integrated either via:
(a) `@DynamicPropertySource` annotations in the test class overriding Spring properties at test
time, or (b) Testcontainers Spring Boot support with `spring.r2dbc.url` set to the TC URL in
`application-test.yml` using the `tc:` protocol.

An implementer does not know from the spec alone which approach to use, nor what
`application-test.yml` should contain.

**Category**: Incomplete

**Severity**: Low — the integration tests will work regardless of which approach is chosen, but
an implementer must invent this detail themselves rather than derive it from the spec.

**Recommendation**: Add the `application-test.yml` content or explicitly state the
`@DynamicPropertySource` approach for Testcontainers integration. Example:
```yaml
# application-test.yml is intentionally empty — Testcontainers URLs are set via @DynamicPropertySource
# in CouponControllerIT using the TC-managed container's JDBC/R2DBC URLs.
```

---

### L6 — `GeoProperties` record is described but its package location is not specified

**Spec reference**: spec.md lines 334–338 (GeoProperties section):
> "A `@ConfigurationProperties(prefix = "geo")` record binding"

**Gap**: The spec names the record `GeoProperties` and its fields but does not specify which
package it belongs to. Based on the package structure diagram it is not listed. Candidates are
`infrastructure/config/` (alongside `GeoLocationConfig`, `WebClientConfig`) or `infrastructure/geo/`.
The feature-spec also does not mention `GeoProperties` at all — it was added in the impl spec
as an implementation decision.

**Category**: Incomplete

**Severity**: Low — a developer would reasonably place it in `infrastructure/config/` and it
would work, but it is an omission for a spec claiming to be the sole reference document.

**Recommendation**: Add `GeoProperties.java` to the package structure diagram under
`infrastructure/config/` and add it to the File Creation Order.

---

### L7 — Concurrency test N value discrepancy between spec and success criteria

**Spec reference**:
- spec.md line 673: `should_not_exceed_maxUses_under_concurrent_redemptions()` — "N=20 parallel
  redemptions on maxUses=10; assert currentUses == 10 exactly after"
- spec.md line 739 (Success Criteria #2): "Concurrent test: N=20 requests on maxUses=10 results
  in exactly 10 successes and 10 COUPON_EXHAUSTED responses"

**Feature-spec reference**: feature-spec.md line 549:
> "launch N threads simultaneously; assert exactly maxUses succeed and the rest get COUPON_EXHAUSTED"
(N is not specified in feature-spec)

**Gap**: This is not a gap — the impl spec is more precise than the feature-spec here and both
the test and success criteria agree on N=20, maxUses=10. This is confirmed correct.

**Category**: No gap. Noting here to confirm the concurrency invariant is fully covered.

---

## Clarification Requests

### CR1 — What does the concurrency test actually assert about COUPON_EXHAUSTED response count?

**Spec reference**: spec.md line 739:
> "N=20 requests on maxUses=10 results in exactly 10 successes and 10 COUPON_EXHAUSTED responses"

**Issue**: This assertion assumes all 20 requests reach the atomic UPDATE. However, some may fail
with `GEO_UNAVAILABLE` (if the mocked geo service is configured to return an error for some
requests) or `COUPON_NOT_FOUND` (impossible here). Since `GeoLocationService` is mocked via
`@MockBean` in integration tests, the test must configure the mock to return a successful
country match for all 20 requests. The spec does not state this explicitly in the test
description (though it is implied by the "N=20 parallel redemptions" framing).

**Question**: Should the concurrency test configure the `@MockBean GeoLocationService` to always
return the coupon's country for all parallel requests? Please confirm the mock setup expectation
for the concurrency test.

---

### CR2 — `@Transactional` on `redeemCoupon`: method annotation or `TransactionalOperator`?

**Spec reference**: spec.md lines 580–583:
> "Use `transactionalOperator.transactional(...)` or reactive `@Transactional`"

**Issue**: These are two different implementation strategies with different transaction scope
characteristics. `@Transactional` on the reactive method wraps the entire Mono chain, making it
difficult to keep Steps 1–5 outside the transaction without explicit chain restructuring.
`TransactionalOperator.transactional(...)` applied only to the Steps 6a/6b sub-chain is precise.

**Question**: Which strategy is prescribed? The spec offers a choice ("or") but the two strategies
have meaningfully different transaction scope implications. Given invariant 2 (geo BEFORE UPDATE,
geo OUTSIDE transaction), `TransactionalOperator` is the only safe choice. Should the spec
prescribe it exclusively?

---

## Extra Features (Implemented but not in Ground-Truth Design)

### E1 — Named `GeoProperties` configuration-properties record

**Evidence**: Not in feature-spec, not in design-decisions.md, first appears in impl spec.

**Assessment**: A good addition. Makes configuration type-safe and overridable. Consistent with
the `${VAR:default}` config standard. No concern.

---

### E2 — `@OpenAPIDefinition` at application class level

**Evidence**: Confirmed in clarifications.md Q3 (answered yes). Not in feature-spec structure.

**Assessment**: Correct. Adds Swagger UI as required. No concern.

---

## Test Coverage Assessment

### Does the specified test suite cover the critical concurrency invariant?

**Finding**: Yes, completely.

The spec specifies two dedicated concurrency integration tests (spec.md lines 673–676):
- `should_not_exceed_maxUses_under_concurrent_redemptions()` with N=20 / maxUses=10 and the
  explicit assertion `currentUses == 10 exactly` directly targets the "never over-redeemed"
  invariant.
- `should_return_ALREADY_USED_on_concurrent_per_user_duplicate()` with N=10 concurrent same-user
  requests and the assertion "exactly 1 succeeds" targets the UNIQUE constraint race.

The implementation mechanism (`CountDownLatch` or `CompletableFuture.allOf()`) is specified.
The assertion granularity (exact count, not just upper bound) matches the success criteria.

### Coverage of all error paths in unit tests?

All 8 `CouponServiceImplTest` methods map to distinct error paths in the 7-step flow.
The `IpApiGeoLocationServiceTest` covers all 4 geo failure modes. `IpAddressExtractorTest` is
specified (covering all branches) without explicit method names — this is the only test class
where the spec delegates the method naming to the implementer.

**Minor gap**: The spec does not list test method names for `IpAddressExtractorTest`, unlike
the other test classes. An implementer must derive the scenarios themselves. Recommend adding
at minimum: null header, empty header, all-private IPs (fallback to remote address), mixed
private+public (first public returned), valid IPv4, IPv6 loopback.

---

## Standards Conflicts Assessment

### Conflicts correctly identified and resolved in spec:

| Conflict | Resolution Quality |
|---|---|
| `docker.md` eclipse-temurin:21-jre → use 25-jre | Correct. Explicitly stated and justified. |
| `spring-boot.md` No JPA/JDBC exclusion rule | Correct. Explains the rule was written for a DynamoDB-only service. |
| `lombok.md` standards | Correct. Per clarifications.md Q2 answer. |

### Conflict present but not resolved in spec:

| Standard | Conflict | Severity |
|---|---|---|
| `naming-conventions.md` `Dto` suffix | DTOs named `Request`/`Response` without explanation | Low (see L2) |
| `jacoco.md` exclusion patterns | Spec uses `**/domain/**` and `**/*Request.class` / `**/*Response.class`; standard uses `**/model/**` and `**/*Dto.class` — the patterns do not match the actual package structure | Low — see below |

**JaCoCo exclusion pattern gap (elaboration)**: The `jacoco.md` standard (lines 7–8) specifies the
80% gate applies to packages: `service`, `consumer`, `controller`, `mapper`, `aspect`, `exception`,
`repository`. The exclusion patterns reference `**/model/**` and `**/*Dto.class`.

The impl spec adapts this for the project's actual packages (`application/`, `api/`, `infrastructure/`)
and exclusion patterns (`**/domain/**`, `**/*Request.class`, `**/*Response.class`, `**/config/**`,
`**/*Application.class`). This adaptation is reasonable and necessary. However, the spec does not
explicitly state that it is adapting the standard — it just lists the adapted rules. A developer
who reads the standard and the spec may wonder which to follow.

**Recommendation**: Add a sentence: "The JaCoCo exclusion patterns below are adapted from
`testing/jacoco.md` for this project's package structure — `domain/` replaces `model/`,
`Request.class`/`Response.class` replace `Dto.class`."

---

## Component Count Verification

The spec claims "~25+ Java source files". Counting from the package structure diagram:

| Package | Files |
|---|---|
| api/ | CouponController.java, GlobalExceptionHandler.java |
| api/dto/ | CreateCouponRequest.java, RedeemCouponRequest.java, CouponResponse.java, RedemptionResponse.java, ErrorResponse.java |
| api/validation/ | CountryCodeValidator.java + @ValidCountryCode annotation (2 files or 1 combined) |
| application/ | CouponService.java, CouponServiceImpl.java, GeoLocationService.java |
| domain/ | Coupon.java, CouponUsage.java, CouponErrorCode.java, CouponException.java |
| infrastructure/persistence/ | CouponRepository.java, CouponRepositoryCustom.java, CouponRepositoryCustomImpl.java, CouponUsageRepository.java |
| infrastructure/geo/ | IpApiGeoLocationService.java |
| infrastructure/config/ | GeoLocationConfig.java, WebClientConfig.java |
| infrastructure/ | IpAddressExtractor.java |
| root package | CouponServiceApplication.java |

Count: ~22 Java files (23 if `@ValidCountryCode` is a separate file, 24 if `GeoProperties` is
included — which it should be per E1 but is not in the diagram).

**Finding**: The claim "~25+" is accurate if `@ValidCountryCode` is a separate file and
`GeoProperties` is included. The spec should add `GeoProperties.java` to the package diagram
(see L6).

---

## Summary of Findings

| ID | Severity | Category | Title |
|---|---|---|---|
| M1 | Medium | Incomplete | `remainingUses` formula does not specify which snapshot of `currentUses` to use |
| M2 | Medium | Incomplete | `coupons_pkey` constraint name undocumented; discrimination logic fragile |
| L1 | Low | Ambiguous | `@Transactional` placement vs. `TransactionalOperator` — method scope contradiction |
| L2 | Low | Extra | DTO naming (`Request`/`Response`) deviates from `naming-conventions.md` without documented decision |
| L3 | Low | Missing (vs feature-spec) | `findByCouponCodeAndUserId` omitted — correct per minimal-impl standard |
| L4 | Low | Inconsistent | Dockerfile COPY path differs from feature-spec; hardcoded JAR name |
| L5 | Low | Incomplete | `application-test.yml` content not specified |
| L6 | Low | Incomplete | `GeoProperties` package location not specified; missing from diagram |
| L7 | — | Confirmed | Concurrency test N=20/maxUses=10 assertion is correct and sufficient |

---

## Recommendations Summary

1. **M1 (must fix)**: Rewrite Step 7 to explicitly state: "`remainingUses = maxUses - currentUses - 1`
   where `currentUses` is the value from the Step 3 SELECT (pre-update snapshot)."
   Or: Change `atomicIncrementUsage` to return the new `current_uses` value and compute
   `remainingUses = maxUses - newCurrentUses`.

2. **M2 (must fix)**: Replace the `coupons_pkey` string-match approach with the complementary-match
   approach: any `DataIntegrityViolationException` that does NOT contain
   `"uq_coupon_usages_per_user"` → 409 `COUPON_ALREADY_EXISTS`. Alternatively, add
   `CONSTRAINT coupons_pkey PRIMARY KEY` explicitly to the V1 migration DDL.

3. **L1 (should fix)**: Prescribe `TransactionalOperator` exclusively; remove the ambiguous
   `@Transactional on redeemCoupon()` statement.

4. **L2 (should document)**: Add one line to Standards Conflicts Resolved table explaining the
   `Request`/`Response` suffix choice.

5. **L5 (should fix)**: Add `application-test.yml` content or explicitly prescribe the
   `@DynamicPropertySource` pattern for Testcontainers.

6. **L6 (should fix)**: Add `GeoProperties.java` to the package structure diagram and File
   Creation Order (between step 8 infrastructure setup items).

7. **L4 (nice to have)**: Switch Dockerfile COPY to wildcard form (`COPY target/*.jar app.jar`).
