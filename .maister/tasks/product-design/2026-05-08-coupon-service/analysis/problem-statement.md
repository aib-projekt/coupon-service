# Problem Statement — Coupon Service

## Core Problem
E-commerce promotions require a reliable mechanism to issue and track discount coupon codes, with geo-based redemption restrictions and concurrency-safe usage counting.

## Problem Statement
Build a production-ready REST API for coupon lifecycle management — creation, retrieval, and redemption — with the following properties:
1. Coupon codes are case-insensitive, alphanumeric (max 64 chars), and globally unique
2. Redemption is capped at `maxUses`, enforced atomically (never exceeded even under concurrent load)
3. Each coupon is restricted to one country; redemptions from other countries are rejected using IP geolocation (fail closed if geo service is unavailable)
4. Coupons optionally enforce per-user single-use via a `perUserLimit` flag; when enabled, `userId` is required in the redemption request
5. All failure modes return structured JSON `{error, message}` responses with machine-readable error codes
6. The service is stateless and horizontally scalable; all persistence in a shared database

## Constraints
- Language: Java 25
- Build: Apache Maven
- No authentication required on any endpoint
- IP geolocation must use a free service
- Duplicate coupon code creation returns HTTP 409 Conflict
- Country codes: ISO 3166-1 alpha-2 format
- Coupon codes: alphanumeric only, max 64 characters, case-insensitive (normalized to uppercase at API boundary)
- Geo service unavailable → fail closed (reject redemption with GEO_UNAVAILABLE error)
- Multiple service instances must produce consistent results (horizontally scalable, shared DB)

## Success Criteria
- `POST /coupons` creates a coupon; returns 409 if code already exists
- `GET /coupons/{code}` returns coupon details including current usage count
- `POST /coupons/{code}/redeem` atomically increments usage and returns distinct error codes:
  - `COUPON_NOT_FOUND` — code doesn't exist
  - `COUPON_EXHAUSTED` — maxUses reached
  - `COUNTRY_NOT_ALLOWED` — request IP is in a disallowed country
  - `ALREADY_USED` — user already redeemed this coupon (when perUserLimit=true)
  - `GEO_UNAVAILABLE` — geolocation service unreachable
- Usage count never exceeds `maxUses` under concurrent load (strict enforcement)
- Service instances are stateless; state lives exclusively in the database

## Key Assumptions
- User identifier (for per-user tracking) is an opaque string provided by the caller — no internal user management
- The geolocation provider returns ISO 3166-1 alpha-2 country codes
- Coupon codes are stored and matched in uppercase (normalized at API ingestion)
- No authentication — API is open; caller is responsible for access control at a higher level
- No coupon expiry date (not in scope for this design)
- No coupon deactivation or deletion endpoint (not in scope)
- No list endpoint (not in scope)

## Design Decisions Made During Exploration
| Decision | Choice | Rationale |
|---|---|---|
| Concurrency enforcement | Strict (atomic DB-level) | Never exceed cap, even under load |
| Geo service failure mode | Fail closed | Country restriction is a hard business rule |
| Country format | ISO 3166-1 alpha-2 | Matches geo API output, compact, validatable |
| Code format | Alphanumeric, max 64 chars | URL-safe, typeable, no escaping edge cases |
| Per-user limit | Configurable per coupon (`perUserLimit` flag) | Some coupons allow multi-use by same user |
| Error response format | `{error, message}` JSON | Machine-readable error codes for clients |
| API scope | Create + Redeem + Get-by-code | Minimal per spec, plus useful read endpoint |
| Duplicate create | 409 Conflict | Clear, predictable, forces intentional code choice |
| Scalability model | Stateless instances + shared DB | Standard horizontal scale pattern |
