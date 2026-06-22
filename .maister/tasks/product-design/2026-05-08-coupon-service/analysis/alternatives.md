# Design Alternatives — Coupon Service REST API

*Generated: 2026-05-08*
*Based on: design-context.md, problem-statement.md, personas.md*

---

## Decision Area 1: Framework & Architecture Pattern

### Alternative 1A: Spring Boot 3 with Spring MVC (Servlet / Blocking)

Spring Boot 3 using the traditional servlet-based web layer (spring-boot-starter-web). Controllers are synchronous, one thread per request, Spring Data JPA handles persistence.

**Strengths**
- Dominant Java ecosystem choice — widest community knowledge, tutorials, StackOverflow coverage
- Spring Data JPA's repository abstraction pairs naturally with JPA entities and declarative transactions (`@Transactional`)
- Spring Boot auto-configuration minimises boilerplate; testing stack (MockMvc, `@SpringBootTest`, `@DataJpaTest`) is mature
- Synchronous thread-per-request model matches the blocking JDBC calls and external HTTP calls to geolocation API without introducing reactive complexity
- Examiners assessing a recruitment task will expect and recognise this pattern

**Weaknesses**
- Starts slower than Quarkus / Micronaut (though irrelevant for a long-running service)
- Thread-pool sizing limits raw throughput if the geolocation HTTP call is slow — each blocked thread counts against the pool
- Slightly heavier memory footprint than lightweight alternatives

**Best when**
- The team is familiar with Spring and wants production-grade, well-understood patterns
- The codebase may grow or be maintained by future engineers who know Spring
- This is a recruitment exercise judged on code quality and architecture, not raw performance

---

### Alternative 1B: Spring Boot 3 with Spring WebFlux (Reactive)

Spring Boot 3 using the reactive web stack — non-blocking I/O via Project Reactor. Controllers return `Mono<T>` / `Flux<T>`, database access via R2DBC.

**Strengths**
- Non-blocking event loop makes the geolocation HTTP call genuinely asynchronous — no thread tied up waiting
- Higher theoretical request-per-second per instance under I/O-heavy workload

**Weaknesses**
- R2DBC + reactive transactions are significantly more complex than JPA — optimistic/pessimistic locking patterns are harder to implement correctly
- Reactive programming has a steep learning curve; code is harder to read, review, and debug
- The geolocation HTTP call is a single blocking point; reactive does not eliminate it, only reschedules it
- Spring Data R2DBC lacks the full richness of Spring Data JPA (no JPQL, fewer query features)
- Over-engineered for a recruitment exercise with no stated throughput requirements

**Best when**
- Request volume is extremely high (thousands of concurrent requests per instance) and the geolocation latency is the dominant cost
- The team is already experienced with reactive programming

---

### Alternative 1C: Quarkus with JAX-RS (REST)

Quarkus framework with RESTEasy Reactive or Classic, Hibernate ORM with Panache, JUnit 5.

**Strengths**
- Fast startup, low memory footprint — excellent for Docker/container deployments
- Panache's active-record or repository pattern simplifies entity code
- First-class native compilation support (GraalVM) for extremely fast cold starts

**Weaknesses**
- Less community material than Spring Boot for recruitment context
- Examiners unfamiliar with Quarkus may not assess code quality accurately
- Requires learning Quarkus-specific conventions (Panache, CDI vs Spring DI)
- Smaller ecosystem of extensions vs Spring Boot starters

**Best when**
- Deployment target is serverless or requires fast cold-start (e.g., AWS Lambda)
- Team has existing Quarkus experience

---

**Recommendation signal**: Alternative 1A (Spring Boot MVC) is the safest and most appropriate choice for this context. It is production-proven, assessment-friendly, and the blocking I/O model is perfectly suited to the serial redeem flow (lookup → geolocate → lock → update).

---

## Decision Area 2: Database & ORM

### Alternative 2A: PostgreSQL + Spring Data JPA / Hibernate

PostgreSQL as the relational database engine; Spring Data JPA with Hibernate as the ORM; `JpaRepository` for basic CRUD; `@Query` or native queries for the atomic counter update.

**Strengths**
- PostgreSQL is free, production-grade, and has best-in-class support for atomic operations, row-level locking (`SELECT ... FOR UPDATE`), and `ON CONFLICT`
- Spring Data JPA repositories dramatically reduce boilerplate
- Flyway/Liquibase integrations are first-class
- `@Transactional` wraps the redeem logic cleanly
- UNIQUE constraint on normalised coupon code enforces duplicate prevention at DB level
- Excellent tooling: pgAdmin, DBeaver, IDE plugins

**Weaknesses**
- ORM adds an abstraction layer that can hide SQL; HQL/JPQL less expressive than native SQL for the atomic counter pattern
- N+1 query risks if fetch strategies are not carefully configured (minor risk for this bounded domain)
- Requires a running PostgreSQL instance for integration tests (solved via Testcontainers)

**Best when**
- The project will run in any standard deployment environment
- Code quality and production-readiness are being assessed

---

### Alternative 2B: PostgreSQL + Spring JDBC Template (no ORM)

PostgreSQL with plain `JdbcTemplate` or `NamedParameterJdbcTemplate`. Entities are simple POJOs mapped manually from `ResultSet`. No ORM layer.

**Strengths**
- Full control over SQL — the atomic `UPDATE coupons SET current_uses = current_uses + 1 WHERE ... AND current_uses < max_uses RETURNING *` is expressed directly, no ORM translation
- Zero Hibernate magic; what you write is what runs
- Lighter dependency footprint

**Weaknesses**
- More boilerplate: manual `RowMapper` implementations, no repository abstraction
- For a recruitment exercise, the reduced abstraction may be seen as more work for less signal
- Loses Spring Data JPA's automatic dirty checking, cascades, and `save()` convenience

**Best when**
- The atomic counter query is the primary concern and the team wants SQL precision
- The domain is so simple (3 tables) that ORM adds no value

---

### Alternative 2C: H2 In-Memory (development/test) + PostgreSQL (production)

H2 in-memory or file-backed database for local development and tests; PostgreSQL for production. Spring profile-based datasource switching.

**Strengths**
- Zero infrastructure to run tests — no Docker, no external DB
- Fast test execution

**Weaknesses**
- H2 dialect differences from PostgreSQL can mask bugs (e.g., H2's `FOR UPDATE` behaviour differs subtly)
- Not suitable as the production database for a horizontally-scalable service (in-memory H2 is per-instance, not shared)
- Concurrency behaviour under H2 is not representative of production
- Using H2 in production would be a design flaw given the horizontal scaling requirement

**Best when**
- Used strictly as a test layer with Testcontainers covering integration tests against real PostgreSQL

---

**Recommendation signal**: Alternative 2A (PostgreSQL + Spring Data JPA) with Testcontainers for integration tests. Use a native `@Modifying @Query` for the atomic counter update where ORM-generated SQL is insufficient.

---

## Decision Area 3: Concurrency / Usage Cap Enforcement Strategy

### Alternative 3A: Atomic SQL UPDATE with Row-Count Check (Recommended)

Issue a single `UPDATE coupons SET current_uses = current_uses + 1 WHERE code = :code AND current_uses < max_uses`. Check the updated row count: if 0, the cap was reached (or the coupon does not exist). No application-level locking.

```sql
UPDATE coupons
SET current_uses = current_uses + 1
WHERE code = :code
  AND current_uses < max_uses
RETURNING current_uses, max_uses;
```

**Strengths**
- Single round-trip to the database — minimal latency on the hot path
- No lock held across application logic (geolocation call, business rule checks); only the row update is atomic
- PostgreSQL guarantees this UPDATE is atomic — two concurrent transactions cannot both succeed when `current_uses = max_uses - 1`
- Scales horizontally: all instances share the same DB, no distributed lock needed
- Simplest implementation: no version columns, no retry loops

**Weaknesses**
- The geolocation check and per-user check must happen *before* this UPDATE; if they fail after the UPDATE, a compensating update is needed — design the order of operations carefully
- Requires the redeem flow to be split: check preconditions (geo, per-user) first, then apply the atomic UPDATE, then return

**Best when**
- The cap must be strictly enforced (never exceeded) with minimal latency
- The database is PostgreSQL (or any RDBMS with atomic row updates)

---

### Alternative 3B: Optimistic Locking with Retry Loop

Add a `@Version` column (integer or timestamp) to the coupon entity. Spring Data JPA / Hibernate manages optimistic locking. On concurrent update collision, catch `OptimisticLockException` and retry.

**Strengths**
- JPA-native pattern — `@Version` annotation is standard
- Works without custom SQL
- Low contention in read-heavy workloads (no lock held between reads)

**Weaknesses**
- Under high coupon redemption contention (popular coupon, burst traffic), many transactions will fail and retry — wasted CPU and DB round-trips
- Retry logic adds code complexity: how many retries? Backoff? What to return after exhausting retries?
- A retry loop can still exceed maxUses if not carefully bounded — requires re-reading the count after each retry
- The version column approach does not naturally express "cap not yet reached" — additional logic needed to distinguish "version conflict" from "cap reached"

**Best when**
- Contention is expected to be low (many distinct coupons, infrequent concurrent hits on the same code)
- Simplicity of JPA annotations is prioritised over custom SQL

---

### Alternative 3C: Pessimistic Locking (`SELECT ... FOR UPDATE`)

Issue a `SELECT ... FOR UPDATE` to lock the coupon row, then check and increment in application logic within the same transaction.

**Strengths**
- Explicit and easy to reason about: only one transaction touches the row at a time
- No retry loops needed — waiters queue on the lock

**Weaknesses**
- Lock held for the duration of the transaction — if the geolocation HTTP call is inside the same transaction, the lock is held for the full network round-trip time
- Under high concurrency, all other requests for the same coupon queue behind the lock holder — throughput bottleneck
- Database lock escalation risk under extreme load
- Geolocation call MUST happen outside the locked transaction to avoid holding locks across network I/O

**Best when**
- Concurrency is genuinely low and correctness clarity is more important than throughput
- The team finds `SELECT ... FOR UPDATE` easier to reason about than atomic UPDATE

---

**Recommendation signal**: Alternative 3A (atomic SQL UPDATE). It is the most correct, most efficient, and best-suited to horizontal scaling. The implementation is straightforward in Spring Data JPA via `@Modifying @Query` with a RETURNING clause or row-count check.

---

## Decision Area 4: IP Geolocation Provider

### Alternative 4A: ip-api.com (HTTP, no key required)

Free tier HTTP JSON API: `http://ip-api.com/json/{ip}?fields=status,countryCode`. No API key. Rate limit: 45 requests/minute per IP on the free tier.

**Strengths**
- No registration or API key required — zero setup friction
- Returns ISO 3166-1 alpha-2 `countryCode` field directly
- Simple JSON response; easy to parse
- Well-documented, widely used in open-source projects

**Weaknesses**
- HTTP only on free tier (no HTTPS) — the IP address is transmitted unencrypted; acceptable for a service-to-service call on a private network, but not ideal for production
- 45 req/min rate limit is low for any real traffic — would be hit quickly under load
- External network call on every redemption request adds latency (~50–150ms typical) and a failure point

**Best when**
- This is a recruitment/prototype exercise where rate limits and HTTPS are not critical constraints
- No budget for a paid geolocation service

---

### Alternative 4B: ipapi.co (HTTPS, no key for low volume)

HTTPS JSON API: `https://ipapi.co/{ip}/country/`. No key for up to 30,000 requests/month (~1,000/day).

**Strengths**
- HTTPS by default — IP address transmitted over encrypted connection
- Returns the country code as a plain text string (simple to parse)
- Slightly higher free tier than ip-api.com for monthly volume
- No API key for modest usage

**Weaknesses**
- Still an external HTTP call on the hot path
- Hard rate limit; exceeding requires a paid plan
- Response format is plain text, not JSON — slightly different parsing from ip-api.com

**Best when**
- HTTPS is required and a modest free tier is acceptable

---

### Alternative 4C: MaxMind GeoLite2 — Local Database File

Download MaxMind GeoLite2-Country.mmdb (free, license required). Use the `com.maxmind.geoip2:geoip2` Java library to look up the country from the IP locally. No network call at runtime.

**Strengths**
- Zero network latency on the hot path — lookup is in-memory (< 1ms)
- No external service dependency — eliminates the "GEO_UNAVAILABLE" failure mode (except for invalid/bogon IPs)
- No rate limiting
- HTTPS/privacy concerns are eliminated (data stays local)
- Database is updated weekly; accuracy is comparable to API-based services

**Weaknesses**
- Requires MaxMind account and license key for download (free, but requires registration)
- Database file is ~6MB and must be bundled with the service or mounted as a volume — complicates Docker image build
- Database must be kept up to date (weekly download recommended) — operational overhead
- GeoLite2 accuracy is slightly lower than commercial databases (acceptable for most use cases)

**Best when**
- Throughput requirements make per-request HTTP calls impractical
- Reliability (no external dependency on the hot path) is prioritised
- Docker deployment makes bundling the .mmdb file straightforward

---

**Recommendation signal**: For a recruitment exercise, Alternative 4A (ip-api.com) is the simplest to demonstrate. However, the service layer should abstract the geolocation provider behind an interface (`GeoLocationService`) so the implementation can be swapped without touching business logic. If production-readiness is emphasised, Alternative 4C (GeoLite2) eliminates a significant operational risk.

---

## Decision Area 5: API Design Style

### Alternative 5A: Resource-Oriented REST with Standard HTTP Semantics

Endpoints map directly to resources and actions follow HTTP method conventions. Response bodies use consistent `{error, message}` envelope for errors and flat resource objects for success.

```
POST   /coupons                  → 201 Created  (body: coupon resource)
GET    /coupons/{code}           → 200 OK       (body: coupon resource)
POST   /coupons/{code}/redeem    → 200 OK       (body: redemption confirmation)
```

Error format:
```json
{ "error": "COUPON_EXHAUSTED", "message": "Coupon has reached its maximum uses." }
```

**Strengths**
- Familiar to any REST API consumer; no bespoke conventions to learn
- `/coupons/{code}/redeem` uses a sub-resource action POST — a common and accepted pattern for non-idempotent operations on resources
- Error `{error, message}` matches the specification requirement and is machine-readable for the e-commerce backend persona
- HTTP 409 for duplicate creation, 404 for not found, 422 or 409 for business rule failures — all conventional

**Weaknesses**
- `POST /coupons/{code}/redeem` is slightly verb-in-URL, which purists flag; a strict RESTful design would use a redemptions resource — but strict REST is over-engineering here

**Best when**
- API consumers are developers who expect standard REST conventions
- The API surface is small and stable (3 endpoints)

---

### Alternative 5B: Command-Oriented Endpoints

Endpoints express operations as commands rather than resources.

```
POST /create-coupon
POST /use-coupon
GET  /get-coupon?code=SPRING20
```

**Strengths**
- Explicit intent: endpoint name leaves no ambiguity about what it does

**Weaknesses**
- Not RESTful — URL space becomes a flat list of verbs
- Less cacheable (all POSTs, no GET with path parameter)
- Harder to document and integrate with standard API tools (OpenAPI, Swagger UI)
- E-commerce backend persona expects REST conventions
- No clear HTTP status code conventions for commands (everything is 200?)

**Best when**
- RPC-style API (e.g., gRPC or JSON-RPC) is the target, not REST

---

### Alternative 5C: Strict REST with Redemptions Sub-Resource

```
POST   /coupons                          → 201
GET    /coupons/{code}                   → 200
POST   /coupons/{code}/redemptions       → 201 (creates a redemption record)
```

Redemption is modelled as creating a `Redemption` resource under a coupon.

**Strengths**
- Fully noun-oriented: no verbs in URLs
- Redemption resource can be returned in the 201 body with its own ID, creation timestamp, userId — useful for audit

**Weaknesses**
- More complex to design: what is the redemption resource's ID? What does GET /redemptions/{id} return?
- Adds a redemption ID concept that the spec does not require
- May over-complicate the response contract for what is essentially a fire-and-forget operation

**Best when**
- Audit trail of individual redemptions is required
- API surface will grow to include redemption listing, cancellation, etc.

---

**Recommendation signal**: Alternative 5A (resource-oriented REST with `/redeem` action). It balances pragmatic REST conventions with minimal complexity. The `{error, message}` envelope is explicitly required by the spec and maps cleanly to the e-commerce backend's error handling logic.

---

## Decision Area 6: Database Migration Tooling

### Alternative 6A: Flyway

Flyway manages schema migrations as numbered SQL files (`V1__init.sql`, `V2__add_coupon_usages.sql`). Spring Boot auto-configures Flyway when `spring-boot-starter-data-jpa` and `flyway-core` are on the classpath.

**Strengths**
- Simplest migration tool for SQL-only teams — migrations are plain SQL files, no XML/YAML
- Spring Boot auto-configuration: add dependency + `db/migration/` folder, done
- Checksum validation prevents accidental modification of applied migrations
- Widely used; strong recruitment context recognition
- Excellent PostgreSQL support

**Weaknesses**
- Rollback support is limited (not built-in for community edition); undo scripts require pro licence
- XML/YAML configuration is not needed, but the naming convention (`V{n}__{desc}.sql`) must be followed strictly

**Best when**
- Migrations are SQL-first and the team prefers plaintext SQL over Java/XML DSLs
- Spring Boot auto-configuration is desired

---

### Alternative 6B: Liquibase

Liquibase manages schema changes via a changelog (XML, YAML, JSON, or SQL format). Spring Boot auto-configures it via `liquibase-core`.

**Strengths**
- More expressive changelog format — changesets can express vendor-neutral DDL (useful if the database might change)
- Built-in rollback support (via `rollback` tags in changesets)
- Supports tagging and diff commands for complex migration workflows

**Weaknesses**
- Higher configuration verbosity than Flyway — XML/YAML changelogs are more lines for the same schema
- Slight learning curve for the changelog format
- For a PostgreSQL-only project, the database-independence benefit is not relevant

**Best when**
- Rollback capability is required by the team's deployment process
- The changelog needs to be reviewed and audited in a structured format
- Multiple database vendors are targeted

---

### Alternative 6C: Manual DDL (no migration tool)

Schema is created by running SQL scripts manually or via a startup script. No migration library.

**Strengths**
- Zero additional dependency
- Full control over DDL execution

**Weaknesses**
- No schema version tracking — impossible to know which scripts have been applied
- Manual process breaks horizontal deployment automation
- Not production-grade; not appropriate for a "production-ready" codebase
- Each new service instance may have a different schema if scripts are not applied consistently

**Best when**
- Throwaway prototypes, never for production code

---

**Recommendation signal**: Alternative 6A (Flyway). It is the simpler, more widely recognised choice for SQL-first PostgreSQL projects with Spring Boot. For this bounded schema (2–3 tables), Liquibase's additional features are unnecessary complexity.

---

## Summary — Recommended Defaults

| Decision Area | Recommended Alternative | Key Trade-off Accepted |
|---|---|---|
| Framework | Spring Boot 3 + Spring MVC | Blocking I/O acceptable; reactive adds complexity without benefit |
| Database + ORM | PostgreSQL + Spring Data JPA | ORM layer accepted; native `@Modifying @Query` for atomic counter |
| Concurrency strategy | Atomic SQL UPDATE (row-count check) | Custom SQL required; no ORM-generated UPDATE for this case |
| Geolocation provider | ip-api.com (prototype) / GeoLite2 (production) | ip-api.com: HTTP-only, rate-limited; GeoLite2: requires .mmdb bundle |
| API design | Resource-oriented REST with `/redeem` action | Minor verb-in-URL compromise for clarity |
| Migration tooling | Flyway | No built-in rollback; acceptable for this bounded schema |

---

## Deferred Ideas

The following ideas arose during analysis but are outside the scope of the current design:

1. **Coupon expiry date** — Adding `validUntil` timestamp would require an additional business rule check in the redeem flow. Worth adding if the spec evolves, but not required now.
2. **Coupon deactivation / soft delete** — A `status` field (ACTIVE / INACTIVE) would allow manual campaign cancellation. Not in scope per the problem statement.
3. **Bulk coupon creation** — `POST /coupons/batch` for campaign managers creating many codes at once. Out of scope but a natural extension.
4. **Geolocation result caching** — Caching `{ip → countryCode}` in a short-lived in-memory cache (Caffeine) or Redis would reduce external API calls under load. Useful optimisation once a geolocation provider is chosen.
5. **Redemption audit log** — A `redemptions` table recording timestamp, userId, IP, and outcome per redemption attempt would support analytics. Not required by the spec but adjacent to the per-user tracking table.
6. **Rate limiting on the redeem endpoint** — Protecting against redemption brute-force or DDoS. Outside scope but a production hardening consideration.
