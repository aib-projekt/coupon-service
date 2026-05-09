# Product Brief — Coupon Service REST API

*Designed: 2026-05-08 | Architecture: Spring Boot 3 + WebFlux + PostgreSQL (R2DBC) + Flyway*

---

## Layer 0: Core Brief

### Problem Statement

Build a production-ready REST API for coupon lifecycle management. The service manages creation, retrieval, and redemption of discount coupon codes with:
- Atomic usage cap enforcement (never exceeded under concurrent load)
- IP-based country restriction, fail closed if geolocation service is unavailable
- Configurable per-user single-use enforcement
- Structured JSON error responses with machine-readable error codes
- Stateless, horizontally scalable design (shared PostgreSQL database)

### Target Users

| Persona | Role | Primary Endpoints |
|---|---|---|
| **Promo Manager** | Creates and monitors promotional coupons | `POST /api/v1/coupons`, `GET /api/v1/coupons/{code}` |
| **E-commerce Backend** | Validates coupons during customer checkout (server-to-server) | `POST /api/v1/coupons/{code}/redeem` |

### Feature Overview

Three REST endpoints:

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/coupons` | Create a new coupon |
| `GET` | `/api/v1/coupons/{code}` | Retrieve coupon details and usage count |
| `POST` | `/api/v1/coupons/{code}/redeem` | Redeem a coupon (atomic, country-restricted) |

**Coupon fields**: `code` (alphanumeric, max 64 chars, case-insensitive), `maxUses`, `currentUses`, `country` (ISO 3166-1 alpha-2), `perUserLimit` (boolean flag), `createdAt`

### Constraints

- Language: Java 25
- Build: Apache Maven
- No authentication on any endpoint
- IP geolocation: ip-api.com (free, no key) — fail closed on unavailability
- Country codes: ISO 3166-1 alpha-2
- Coupon codes: alphanumeric, normalized to uppercase at API boundary
- Duplicate coupon code creation: 409 Conflict
- Framework: Spring Boot 3 + WebFlux (reactive, non-blocking)

### Success Criteria

- `POST /api/v1/coupons` creates a coupon; returns 409 if code already exists
- `GET /api/v1/coupons/{code}` returns current state including usage count (case-insensitive lookup)
- `POST /api/v1/coupons/{code}/redeem` atomically increments usage and returns structured error on any failure:
  - `COUPON_NOT_FOUND` (404) — code doesn't exist
  - `COUPON_EXHAUSTED` (409) — max uses reached
  - `COUNTRY_NOT_ALLOWED` (403) — user IP from wrong country
  - `ALREADY_USED` (409) — user already redeemed (when `perUserLimit=true`)
  - `GEO_UNAVAILABLE` (503) — geolocation service unreachable
- Usage count **never exceeds `maxUses`** under concurrent load — verified by concurrency integration tests
- Multiple service instances produce consistent results against a shared PostgreSQL database

### Acceptance Criteria

- [ ] All three endpoints return correct responses for the happy path
- [ ] All error codes listed above are returned with correct HTTP status codes
- [ ] `current_uses` never exceeds `max_uses` when N concurrent redemption requests are made simultaneously
- [ ] Coupon code lookup is case-insensitive (SPRING20 = spring20 = Spring20)
- [ ] `perUserLimit=false` coupons: `userId` not required and no per-user tracking
- [ ] `perUserLimit=true` coupons: `userId` required; second redemption by same user returns `ALREADY_USED`
- [ ] Geolocation service failure → `GEO_UNAVAILABLE` (fail closed, no redemption allowed)
- [ ] JaCoCo coverage gate passes at >= 80% instruction and branch coverage
- [ ] Service starts and serves requests with `docker compose up`

---

## Layer 1: Persona Cards

### Promo Manager

**Goals**: Create coupons reliably; monitor usage count during campaigns; ensure geo restrictions work  
**Pain points**: Exceeding usage caps under load; ambiguous error messages on creation failure  
**Key journey**: Create coupon via API → share code with users → monitor via GET endpoint → campaign ends naturally at maxUses

### E-commerce Backend

**Goals**: Fast redemption with deterministic error codes; map errors to user-facing messages without string parsing  
**Pain points**: Geolocation service on the hot path; inconsistent results under concurrent checkout load  
**Key journey**: User enters code at checkout → call `POST .../redeem` with `X-Forwarded-For` and optional `userId` → apply discount on 200 OR show user-friendly message based on error code

> Full persona detail: `analysis/personas.md`

---

## Layer 2: Design Decisions

| Decision | Selected | Key Trade-off |
|---|---|---|
| **Framework** | Spring Boot 3 + **WebFlux** | Non-blocking throughout; R2DBC required; higher complexity than MVC |
| **Database** | PostgreSQL + **Spring Data R2DBC** | Non-blocking; less ORM convenience; DatabaseClient for atomic query |
| **Concurrency** | **Atomic SQL UPDATE** (`current_uses < max_uses`) | Custom SQL; single round-trip; cap guaranteed never exceeded |
| **Geolocation** | **ip-api.com** via `GeoLocationService` interface | HTTP-only, rate-limited; interface enables easy swap to GeoLite2 |
| **API style** | **Resource-oriented REST** with `/redeem` action | Minor verb-in-URL; `{error, message}` error envelope |
| **Migrations** | **Flyway** SQL files | No built-in rollback; sufficient for 2-table schema |

> Full alternatives detail: `analysis/alternatives.md` | Decision rationale: `analysis/design-decisions.md`

### Critical Architectural Invariants

1. Coupon codes normalized to **uppercase** at the API boundary before any lookup or storage
2. **Geolocation check runs before the atomic UPDATE** — avoids incrementing the counter then failing on geo
3. **Database transaction scope is minimal**: covers only the atomic UPDATE + optional INSERT (Steps 6a/6b); geo call and existence checks are outside the transaction
4. **`GeoLocationService` is an interface** — zero coupling between business logic and ip-api.com
5. **X-Forwarded-For parsing** uses the first non-private IP in the chain (client IP, not datacenter IP)

---

## References

All design artifacts produced during this workflow:

| Artifact | Path | Content |
|---|---|---|
| Design Context | `analysis/design-context.md` | Unified synthesis of recruitment spec + project docs |
| Problem Statement | `analysis/problem-statement.md` | Problem, constraints, success criteria, all 9 design decisions from exploration |
| Personas | `analysis/personas.md` | Promo Manager + E-commerce Backend persona cards and journeys |
| Alternatives | `analysis/alternatives.md` | 16 alternatives across 6 decision areas with full trade-off analysis |
| Design Decisions | `analysis/design-decisions.md` | Selected approach per decision area with rationale and invariants |
| Feature Spec | `analysis/feature-spec.md` | 7 implementation-ready sections: data model, API contract, redeem flow, service architecture, geolocation, testing, infrastructure |
