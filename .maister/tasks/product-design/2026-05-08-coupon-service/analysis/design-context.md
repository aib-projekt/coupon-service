# Design Context — Coupon Service

*Synthesized from: recruitment specification + project tech-stack + project architecture*
*Date: 2026-05-08*

---

## 1. Primary Source: Recruitment Specification

**Original language**: Polish (key points translated and summarized below)

### Core Goal
Design and implement a REST service for managing discount coupons. Two primary operations:
1. **Create a coupon** (no authentication required)
2. **Register coupon usage** by a user

### Coupon Data Model (required fields)
| Field | Description |
|---|---|
| Coupon code | Unique identifier, case-insensitive |
| Creation date | When the coupon was created |
| Max uses | Maximum allowed redemption count |
| Current uses | Running count of how many times it's been used |
| Country | The country for which the coupon is valid |

### Business Rules
1. **Case-insensitive uniqueness**: `SPRING` and `spring` are the same coupon code
2. **Usage cap**: Limited to `maxUses` — first-come-first-served race condition must be handled safely
3. **Country restriction**: Coupon can only be used by requests originating from the coupon's country (determined via IP address using any free geolocation service)
4. **Error responses required for**:
   - Coupon code does not exist
   - Coupon has reached its maximum uses
   - Request originates from a disallowed country
   - User has already used this coupon (if optional feature enabled)
5. **Optional feature**: One user may use a coupon only once — request includes a user identifier (any format) + coupon code

### Non-Functional Requirements
- **Scalability**: Solution must be horizontally scalable
- **Persistence**: All data stored in a database (any free DB engine allowed)
- **Language**: Java or Kotlin
- **Build**: Maven or Gradle
- **Thread safety**: Must work correctly in multi-threaded production environment
- **Code quality**: Production-grade quality, thoughtful architecture, design patterns, not a minimal placeholder implementation
- **No framework restrictions**: Any freely available libraries/frameworks allowed

---

## 2. Project Context (from .maister/docs)

### Tech Stack (current state)
- **Language**: Java 25 (configured in `pom.xml`)
- **Build**: Apache Maven with Maven Wrapper
- **Framework**: Not yet selected — Spring Boot strongly recommended
- **Database**: Not yet selected — PostgreSQL recommended for relational + concurrency support
- **Testing**: Not yet configured — JUnit 5 + Mockito recommended
- **IDE**: IntelliJ IDEA
- **VCS**: Git

### Architecture (planned)
- **Pattern**: Layered REST microservice
- **Layers**:
  - API Layer → HTTP controllers
  - Service Layer → Business rules, coupon lifecycle
  - Domain Layer → Entities, value objects
  - Persistence Layer → Repository interfaces + implementations
- **Data flow**: HTTP request → API → Service → Domain → Persistence → Response
- **DB migrations**: Flyway or Liquibase planned
- **Deployment**: Docker target

---

## 3. Cross-Reference Insights

### Concurrency is the Key Design Challenge
The usage cap + first-come-first-served rule creates a classic **concurrent counter problem**. Multiple requests can race to be the "last" use. Solutions range from optimistic locking, pessimistic locking, database-level atomic operations, or distributed locks. Given scalability requirements, this must be designed carefully.

### IP Geolocation is a Runtime External Dependency
Country-based restriction requires calling a free IP geolocation API per coupon-use request. This introduces:
- **Network latency** on the hot path
- **External service failure** modes
- **Privacy considerations** (IP address processing)
- **Rate limiting** from free-tier geolocation providers

### Case-Insensitive Code Storage Strategy
Storing coupon codes in normalized form (uppercase or lowercase) in the database is the simplest approach. Normalization must happen at the boundary (API layer) to ensure consistency regardless of input.

### Optional Per-User Tracking Needs Its Own Table
If the optional "one user per coupon" feature is implemented, a separate junction table (`coupon_usages`) mapping `user_id + coupon_code` is needed with a unique constraint — this naturally prevents double use at the database level.

---

## 4. Implications for Design

1. **Framework choice** will drive the shape of everything — Spring Boot with Spring Data JPA is the natural fit for this team's Java stack
2. **Database transactions + locking strategy** must be decided early — drives how the usage counter is updated safely
3. **IP geolocation provider** must be selected and failure modes designed (fail open vs fail closed)
4. **Coupon code normalization** strategy must be consistent everywhere (API input, DB storage, lookups)
5. **Error response format** should be standardized across all failure cases for consistent API contract
6. **The optional feature** (per-user tracking) should be designed as a first-class citizen even if marked optional — it's a common production requirement
7. **Scalability** means stateless service design; all shared state lives in the database

---

## 5. Open Questions for Problem Exploration

- Should coupon codes have a format constraint (alphanumeric only, length limits)?
- What is the expected request volume / concurrency level?
- Should "country" use ISO 3166-1 alpha-2 codes (PL, DE, US)?
- What happens when the geolocation service is unavailable — fail open (allow) or fail closed (reject)?
- Is the user identifier in the optional feature opaque (any string) or typed (UUID, email)?
- Should coupon creation be idempotent (re-submitting same code = update vs error)?
- Should there be an expiry date on coupons?
- Should there be a way to deactivate/delete a coupon?
