# Train Travel Microservices — Demo Gateway & Mock Estate

## What this is

A **Spring Boot gateway application** that implements the [Train Travel API](../../train-travel-openapi.yaml)
(OpenAPI 3.1, v1.2.1 — stations, trips, bookings, payments) end-to-end, backed **not by a database but by four
invented external microservices**, each of which is mocked in WireMock Cloud:

```
                        ┌──────────────────────────────────────────────┐
                        │  Spring Boot gateway (Kotlin, port 8080)     │
                        │  implements the public Train Travel API     │
                        └───┬──────────┬──────────┬──────────┬────────┘
              OAuth2 JWT ───┼──────────┼──────────┼──────────┼─── (JWKS validation)
                            ▼          ▼          ▼          ▼
   wm-train-travel-     Stations     Trips      Bookings   Payments (SOAP 1.1)
   stations/-trips/     Service      Service    Service    "PayRail"
   bookings/-payments/  (REST)       (REST)     (REST)     (unstructured mock)
   -webhook
                            │          │          │          │
                            └── all mocked in WireMock Cloud ──┘

   wm-google-* (mock API 5e5y3)  — Google OAuth2 template mock (auth code flow + JWKS)
   wm-train-travel-webhook       — open POST sink, receives the newBooking webhook
```

The gateway is the only thing developers run locally. Everything downstream is a WireMock Cloud mock API,
so the demo doubles as a showcase of WireMock Cloud capabilities: data-driven stubs (CSV), stateful stubs
(key-value store with per-user contexts), response templating (including SOAP and JWT generation),
and unstructured SOAP mocking.

## Why it exists

- **The WireMock demo story**: "here's a realistic microservices system where *every* dependency is mocked" —
  record from it, break it with chaos settings, virtualise it, contract-test against it.
- The four downstream APIs are deliberately **domain-shaped and awkward** (different field names, natural keys,
  a WSDL-era SOAP payment gateway) because mocking APIs you don't control is the real-world pain WireMock solves.

## User journey (summary)

A demo operator obtains an OAuth2 token from the mocked Google IdP (browser-style login form), then walks the
API: search stations → find trips between two stations on a date → create a booking (which fires the `newBooking`
webhook at the receiver mock) → pay for it (SOAP saga; on success the booking is confirmed and survives expiry) →
list/get/delete bookings. Full journey including all failure paths: see [`v1-user-journey.md`](v1-user-journey.md).

## Scope boundary

**In scope (v1)**

- All 8 public operations: `get-stations`, `get-trips`, `get-bookings`, `create-booking`, `get-booking`,
  `delete-booking`, `create-booking-payment`, plus the outbound `newBooking` webhook.
- Real OAuth2: authorization-code flow against the Google OAuth2 template mock, RS256 JWT validation in the
  gateway, `read`/`write` scope enforcement (genuine 401/403).
- Six WireMock Cloud mock APIs (five new + the existing IdP `5e5y3`).
- Demo script: README curl walkthrough **and** a Postman collection.

**Out of scope (v1)**

- `application/xml` responses on the public API — JSON only (spec's XML schemas untouched).
- Rate limiting — no `RateLimit`/`Retry-After` headers, 429 never returned (documented deviation).
- `409 Conflict` on booking creation — documented but never fired.
- Validation that a booked `trip_id` actually exists.
- Baked-in chaos/fault stubs — use WireMock Cloud's chaos settings toggled in the UI for ad-hoc resilience demos.

## Known simplifications (all deliberate, confirmed in interview)

| # | Simplification | Rationale |
|---|----------------|-----------|
| 1 | No rate limiting (no 429, no RateLimit headers) | Keeps gateway lean; spec deviation is documented |
| 2 | 409 never fired on duplicate bookings | KV state store can't query for duplicates on create |
| 3 | `trip_id` not validated on booking creation | Avoids reverse-mapping public trip UUID → connectionId |
| 4 | `Trip.price` is a bare number; downstream currency dropped | Matches the public schema (no currency field on Trip) |
| 5 | Bicycle/dog trip filtering happens gateway-side, post-pagination | Safe because each route has ≤ 4 connections (< page size) |
| 6 | Text search + geo proximity for stations is gateway-side, not in the mock | Full dataset is ~20 rows; keeps mock's data-exists query simple |

## Coverage mapping

| Feature / concern | Covered by |
|---|---|
| `GET /stations` | Gateway `StationsController` → Stations Service mock (CSV data-driven stubs) |
| `GET /trips` | Gateway `TripsController` → Trips Service mock (CSV data-driven stubs) |
| `POST /bookings` (+ webhook fire) | Gateway `BookingsController` → Bookings Service mock (stateful) + Webhook Receiver mock |
| `GET /bookings` | Gateway → Bookings Service mock (`listState`, per-user context) |
| `GET /bookings/{id}` / `DELETE /bookings/{id}` | Gateway → Bookings Service mock (`require-state`); expiry enforced in gateway |
| `POST /bookings/{id}/payment` | Gateway saga: Bookings mock (validate) → PayRail SOAP mock (`ProcessPayment`) → Bookings mock (`confirm`) |
| Booking expiry (1 hour, HELD only) | Gateway (checks `expiresAt` from Bookings mock; CONFIRMED bookings never expire) |
| `newBooking` webhook delivery | Gateway fire-and-forget POST → Webhook Receiver mock (open URL match) |
| OAuth2 token issuance | Google OAuth2 template mock (`5e5y3`) + Token stub extension (scoped JWT access token) |
| OAuth2 token validation (401) | Spring Security resource server, JWKS from mock `/oauth2/v3/certs` |
| Scope enforcement (403) | Spring method security on write operations (`SCOPE_write`) |
| Public error model (RFC 7807) | Gateway `@RestControllerAdvice` — single `Problem` mapper |
| Downstream failures (500) | Gateway maps downstream 5xx/timeouts to public 500 `Problem` |
| Deterministic station/trip UUIDs | Gateway UUID v5 scheme (see `architecture.md`) |
| Cache-Control headers | Gateway response headers (stations 3600 / trips 60 / bookings+payments no-store) |
| Demo script (curl) | `README.md` demo walkthrough |
| Demo script (Postman) | Postman collection in repo |
| Mock estate creation | Build phase via WireMock Cloud MCP (see `architecture.md` § Mock estate) |

## Document index

| Document | Contents |
|---|---|
| [`v1-user-journey.md`](v1-user-journey.md) | Happy path + every non-happy flow, from the demo operator's perspective |
| [`v1-public-api.md`](v1-public-api.md) | Per-endpoint gateway spec: fields, validation, status codes, sorting, pagination, examples |
| [`v1-downstream-services.md`](v1-downstream-services.md) | The four invented downstream contracts + IdP + webhook receiver, with WireMock Cloud stub strategy for each |
| [`architecture.md`](architecture.md) | The integration map: layers, config, security, data flow, framework patterns, codegen steps |

**Note**: this is a pure backend/API project — there is no frontend, so the usual four component states
(loading/error/empty/data-ready) translate here into the per-endpoint empty-array and error responses specified
in `v1-public-api.md`.