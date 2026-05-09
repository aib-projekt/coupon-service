# Personas — Coupon Service

## Persona 1: Promo Manager

| | |
|---|---|
| **Name** | Promo Manager |
| **Role** | Marketing team member or ops engineer managing promotional campaigns |
| **Primary endpoint** | `POST /coupons` (create), `GET /coupons/{code}` (monitor) |

### Goals
- Create coupons quickly and reliably before campaign launch
- Monitor usage count during an active campaign
- Ensure country-specific campaigns stay within geo boundaries
- Know immediately when creation fails and why (clear error messages)

### Pain Points
- Race conditions that cause usage count to exceed maxUses
- Incorrect country restrictions causing legitimate users to be blocked
- Ambiguous error messages when creation fails (duplicate code, invalid country, etc.)

### Key Journey
1. Campaign launches → creates coupon(s) via `POST /coupons` with code, maxUses, country, perUserLimit
2. Shares coupon codes with target users via email, landing page, or promotional material
3. During campaign: periodically checks usage via `GET /coupons/{code}` to monitor uptake
4. Campaign naturally ends when maxUses is reached; no manual deactivation needed

---

## Persona 2: E-commerce Platform Backend

| | |
|---|---|
| **Name** | E-commerce Backend |
| **Role** | Internal service that processes checkout, calls coupon service during order validation |
| **Primary endpoint** | `POST /coupons/{code}/redeem` |

### Goals
- Fast, reliable redemption with deterministic error codes
- Predictable behavior under traffic spikes (concurrent checkout sessions)
- Map error codes directly to user-facing messages without additional parsing
- Forward user IP via `X-Forwarded-For` header for country restriction

### Pain Points
- Geolocation service being a point of failure on the hot path (checkout flow)
- Ambiguous error responses requiring string matching to understand failure mode
- Inconsistent redemption results under concurrent load (multiple users, same coupon)

### Key Journey
1. End user enters coupon code at checkout in the e-commerce app
2. E-commerce backend calls `POST /coupons/{code}/redeem` with:
   - `X-Forwarded-For: <user IP>` header
   - Request body: `{"userId": "<opaque-id>"}` (if perUserLimit is enabled on coupon)
3. On `200 OK` → applies discount to order and confirms to user
4. On `4xx` with `{error, message}` → maps error code to user-friendly message:
   - `COUPON_NOT_FOUND` → "This coupon code doesn't exist"
   - `COUPON_EXHAUSTED` → "This coupon is no longer available"
   - `COUNTRY_NOT_ALLOWED` → "This coupon isn't available in your region"
   - `ALREADY_USED` → "You've already used this coupon"
   - `GEO_UNAVAILABLE` → "Unable to verify your location. Please try again."

---

## Key Design Implications from Personas

1. **IP forwarding is required**: Redemption API must read `X-Forwarded-For`, not the connecting IP — all calls are server-to-server
2. **Error codes must be machine-readable**: E-commerce backend maps codes to UX messages — string error codes are essential
3. **Concurrency is a real concern**: Multiple checkout sessions can race for the same coupon — strict enforcement is critical
4. **Geolocation failure affects the checkout flow**: Fail-closed behavior must return a retriable error, not a permanent rejection
