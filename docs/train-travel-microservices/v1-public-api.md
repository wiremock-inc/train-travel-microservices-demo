# v1 — Public API (Gateway Behaviour Specification)

The gateway is the sole implementation of `train-travel-openapi.yaml` (v1.2.1). This document specifies the
behaviour of every public operation: request validation, response fields (including nullables and edge values),
status codes with their causes, sorting, pagination, headers, and examples. Field names follow the spec
exactly (`snake_case`); all request/response bodies are JSON.

## Cross-cutting rules

### Auth (every operation)

| Concern | Behaviour |
|---|---|
| Token format | `Authorization: Bearer <RS256 JWT>` issued by the Google OAuth2 mock's `/token` endpoint |
| Validation | Local, in the gateway: signature via JWKS (`{auth-base}/oauth2/v3/certs`), `exp`, `iss` (= auth mock base URL). No introspection round-trip. |
| Scopes | JWT `scope` claim (space-separated). `SCOPE_read` required on all GETs; `SCOPE_write` required on `POST /bookings`, `DELETE /bookings/{id}`, `POST /bookings/{id}/payment`. Enforced with Spring method security. |
| 401 body | `Problem` (below), plus `WWW-Authenticate: Bearer` |
| 403 body | `Problem` (below) — used **only** for scope failures; foreign bookings are 404 |

### Error body (all non-2xx except 204)

RFC 7807 `Problem` as `application/problem+json` (spec components):

```json
{
  "type": "https://example.com/errors/bad-request",
  "title": "Bad Request",
  "status": 400,
  "detail": "The search date specified is in the past."
}
```

`type` URLs per status: `…/errors/bad-request`, `…/errors/unauthorized`, `…/errors/forbidden`,
`…/errors/not-found`, `…/errors/conflict`, `…/errors/too-many-requests` (never used in v1),
`…/errors/internal-server-error`. `instance` = the request path. `detail` always set (no TBD strings);
validation failures name the offending field.

### Status-code cause map

| Status | Causes |
|---|---|
| 400 | Field validation failures; malformed JSON; `date` in the past; unknown origin/destination station; payment declined; SOAP validation fault; paying an already-confirmed booking |
| 401 | Missing, malformed, wrong-key, or expired bearer token |
| 403 | Valid token, missing required scope (write ops only) |
| 404 | Unknown booking id; booking owned by a different user; HELD booking past `expiresAt`; (DELETE only) same causes |
| 409 | Never fired in v1 (documented cut) |
| 429 | Never fired in v1 (rate limiting cut) |
| 500 | Downstream 5xx, SOAP-level failures, or downstream timeout (connect 5s / read 10s) |

### Pagination (all list endpoints)

`page` (int ≥ 1, default 1), `limit` (int 1–100, default 10). Violations → 400.
`links.next` present only if more pages exist; `links.prev` present only if `page > 1`.
All `links.*` are absolute URLs built from the configured public base URL (default `http://localhost:8080`).

### Response headers

| Endpoint | `Cache-Control` |
|---|---|
| `GET /stations` | `max-age=3600` |
| `GET /trips` | `max-age=60` |
| Bookings & payments (all methods) | `no-store` |

`RateLimit`/`Retry-After` are **not** emitted in v1 (documented deviation from the spec).

---

## `GET /stations` — `get-stations`

**Auth:** `SCOPE_read`.

**Query parameters**

| Param | Type | Required | Validation | Behaviour |
|---|---|---|---|---|
| `page` | int | no | ≥ 1 | Pagination |
| `limit` | int | no | 1–100 | Pagination |
| `search` | string | no | — | Case-insensitive substring match on station **name or address**, gateway-side |
| `country` | string | no | ISO 3166-1 alpha-2 (2 letters, case-insensitive → upper) | Exact filter, forwarded to stations service |
| `coordinates` | string | no | `"<lat>,<lon>"`, both decimals, lat ∈ [-90, 90], lon ∈ [-180, 180] | **Changes sort order** to great-circle distance from the point (gateway-side); invalid format → 400 |

**Response `200`:**

```json
{
  "data": [
    {
      "id": "efdbb9d1-02c2-4bc3-afb7-6788d8782b1e",
      "name": "Berlin Hauptbahnhof",
      "address": "Invalidenstraße 10557 Berlin, Germany",
      "country_code": "DE",
      "timezone": "Europe/Berlin"
    }
  ],
  "links": { "self": "http://localhost:8080/stations?page=1", "next": "http://localhost:8080/stations?page=2" }
}
```

- `data`: array (empty array when no matches — the empty state; never null).
- `Station.id`: deterministic UUID v5 of the station's `stationCode` (see `architecture.md`) — **stable across calls and restarts**.
- `timezone`: always present (IANA).
- Sort: `name` ascending (case-insensitive); **distance ascending** when `coordinates` given (ties by name).
- No results beyond the last page → empty `data` with `self` link only.

**Errors:** 400 (bad params), 401, 403, 500.

---

## `GET /trips` — `get-trips`

**Auth:** `SCOPE_read`.

**Query parameters**

| Param | Type | Required | Validation | Behaviour |
|---|---|---|---|---|
| `origin` | uuid | **yes** | Must resolve to a known station | Resolved to a station code for the trips service; unresolvable → 400 |
| `destination` | uuid | **yes** | Must resolve to a known station | As above |
| `date` | date-time | **yes** | ISO 8601; **must not be in the past** → 400 | The day searched (in origin station's timezone per spec) |
| `bicycles` | boolean | no (default false) | — | When `true`, drop trips with `bicycles_allowed=false` (gateway-side) |
| `dogs` | boolean | no (default false) | — | When `true`, drop trips with `dogs_allowed=false` (gateway-side) |
| `page`, `limit` | int | no | as above | Pagination (downstream, ordered by departure) |

**Response `200`:**

```json
{
  "data": [
    {
      "id": "ea399ba1-6d95-433f-92d1-83f67b775594",
      "origin": "efdbb9d1-…",
      "destination": "b2e783e1-…",
      "departure_time": "2026-10-01T10:00:00Z",
      "arrival_time": "2026-10-01T16:00:00Z",
      "operator": "Deutsche Bahn",
      "price": 89.5,
      "bicycles_allowed": true,
      "dogs_allowed": false,
      "links": {
        "self": "http://localhost:8080/trips/ea399ba1-…",
        "origin": "http://localhost:8080/stations/efdbb9d1-…",
        "destination": "http://localhost:8080/stations/b2e783e1-…"
      }
    }
  ],
  "links": { "self": "http://localhost:8080/trips?origin=…&destination=…&date=…", "next": "…" }
}
```

- `Trip.id`: deterministic UUID v5 of (`from`, `to`, `scheduledDeparture`) — stable across calls/restarts.
- `price`: number (never null); the downstream `fare.currency` is dropped (documented simplification).
- `departure_time`/`arrival_time`: ISO 8601 with timezone offset.
- **Empty state:** route/date with no connections → `200`, `"data": []`, `self` link only.
- Sort: `departure_time` ascending.
- **No `GET /trips/{id}` endpoint exists** in the public spec — `links.self` on trips is a notional self link
  (it resolves to nothing; the gateway does not implement it). The `origin`/`destination` links resolve to
  stations in the `GET /stations` result set, not to a `GET /stations/{id}` endpoint (also not in the spec).

**Errors:** 400 (missing/invalid params, past date, unknown station uuids), 401, 403, 500.

---

## `POST /bookings` — `create-booking`

**Auth:** `SCOPE_write`. `Cache-Control: no-store`.

**Request body** (all fields required):

| Field | Type | Validation |
|---|---|---|
| `trip_id` | uuid | Format only — **existence not validated** (documented simplification); stored opaquely as the reservation's `tripRef` |
| `passenger_name` | string | Non-empty, ≤ 200 chars |
| `has_bicycle` | boolean | Required (default false per spec examples — omitted/null → false; explicit non-boolean → 400) |
| `has_dog` | boolean | As above |

**Response `201`** (`Location` header: booking URL):

```json
{
  "id": "1725ff48-ab45-4bb5-9d02-88745177dedb",
  "trip_id": "ea399ba1-…",
  "passenger_name": "Jane Smith",
  "has_bicycle": true,
  "has_dog": false,
  "links": { "self": "http://localhost:8080/bookings/1725ff48-…" }
}
```

- `id` is minted by the bookings service (UUID) and echoed verbatim.
- Internally the reservation is created `HELD` with `expiresAt = now + 1h`; these fields are **not public**
  (the public `Booking` schema has no status/expiry fields) — expiry is observable only behaviourally (404s).
- **Side effect:** the gateway POSTs the `newBooking` webhook (body = the 201 body exactly, i.e. booking +
  `links.self`) to the configured webhook URL, fire-and-forget (3s timeout; failure logs a warning, never
  fails the 201).

**Errors:** 400 (validation), 401, 403, 409 (never fired), 500.

---

## `GET /bookings` — `get-bookings`

**Auth:** `SCOPE_read`. `Cache-Control: no-store`.

- **Scope:** only bookings belonging to the authenticated user (JWT `sub` → `X-User-Id` downstream).
  Other users' bookings are invisible, not 403.
- Query: `page`, `limit` (as above).
- Sort: creation order (oldest first) — the order the bookings service's state store returns them;
  gateway applies page slicing.
- Response `200`: `{ "data": [Booking…], "links": { self, next?, prev? } }` — identical `Booking` shape to
  `POST /bookings` responses (id, trip_id, passenger_name, has_bicycle, has_dog, links.self).
- **Empty state:** user with no bookings → `200`, `"data": []`, `self` link only.

**Errors:** 401, 403, 500 (400 for bad pagination params).

---

## `GET /bookings/{bookingId}` — `get-booking`

**Auth:** `SCOPE_read`. `Cache-Control: no-store`.

- `bookingId`: path uuid; malformed uuid → 400.
- Returns the booking (same shape as above) if it exists, belongs to the user, and is not an expired `HELD`
  hold; otherwise **404**.
- Expired-HELD rule: if reservation `status == "HELD"` and `expiresAt < now` → 404. `CONFIRMED` bookings
  never 404 on expiry grounds (that is the observable effect of paying).

**Errors:** 400, 401, 403, 404, 500.

---

## `DELETE /bookings/{bookingId}` — `delete-booking`

**Auth:** `SCOPE_write`.

- Same lookup rules as `GET` (exists + owned + not expired-HELD), else 404.
- Success: `204`, no body. The reservation state is deleted in the bookings service (hold released).

**Errors:** 400, 401, 403, 404, 500.

---

## `POST /bookings/{bookingId}/payment` — `create-booking-payment`

**Auth:** `SCOPE_write`. `Cache-Control: no-store`.

This is the **saga endpoint**. Sequence: fetch reservation → pre-validate → SOAP `ProcessPayment` →
on APPROVED confirm reservation → respond. On DECLINED/fault the reservation is left `HELD`.

**Pre-validation (400 before any SOAP call):**
- booking exists, is owned by the user, and is not an expired HELD hold (else 404)
- booking is `HELD`, not `CONFIRMED` — paying an already-paid booking → 400 *"Booking has already been paid"*
- request body validation (below)

**Request body:**

| Field | Type | Validation |
|---|---|---|
| `amount` | number | > 0 (spec `exclusiveMinimum: 0`) |
| `currency` | string enum | One of `bam bgn chf eur gbp nok sek try` (lowercase per spec; forwarded upper-cased) |
| `source.object` | string | `card` or `bank_account` |
| card: `name` | string | Required |
| card: `number` | string | Required; digits only, 12–19 chars (Luhn not enforced — mock world) |
| card: `cvc` | string | Required; 3–4 digits; **write-only** |
| card: `exp_month` | int | Required, 1–12 |
| card: `exp_year` | int | Required, ≥ current year |
| card: `address_line1`, `address_line2` | string | Optional; **write-only** |
| card: `address_city` | string | Optional |
| card: `address_country` | string | Required (2-letter) |
| card: `address_post_code` | string | Optional |
| bank: `name`, `number` | string | Required (number: digits) |
| bank: `sort_code` | string | Required, exactly 6 digits |
| bank: `account_type` | enum | `individual` or `company` |
| bank: `bank_name` | string | Required |
| bank: `country` | string | Required (2-letter) |

**Response `200`** (card example):

```json
{
  "id": "2e3b4f5a-6b7c-8d9e-0f1a-2b3c4d5e6f7a",
  "amount": 89.5,
  "currency": "eur",
  "source": {
    "object": "card",
    "name": "J. Doe",
    "number": "************4242",
    "exp_month": 12,
    "exp_year": 2027,
    "address_country": "gb",
    "address_post_code": "N12 9XX"
  },
  "status": "succeeded",
  "links": { "booking": "http://localhost:8080/bookings/1725ff48-…" }
}
```

- `id` = the SOAP `transactionId` (a UUID minted by the mock).
- `source.number` is the **masked** value returned by PayRail — the gateway never emits a raw PAN.
- `status`: `"succeeded"` on APPROVED. (`"failed"`/`"pending"` exist in the schema but are never returned:
  declines surface as 400.)
- Write-only fields (`cvc`, `address_line1`, `address_line2`) are omitted from responses.
- `address_city` is included when supplied.

**Payment outcome drivers (test data):**

| Input | SOAP outcome | Public result |
|---|---|---|
| Card `4242424242424242` (or any card not listed) | APPROVED | 200 + booking CONFIRMED |
| Card `4000000000000002` | DECLINED (`card_declined`) | 400 `Problem`, booking stays HELD |
| Any `bank_account` source | APPROVED | 200 + booking CONFIRMED |

**Errors:** 400 (validation / declined / fault / already-paid), 401, 403, 404 (unknown/foreign/expired booking), 500.

---

## Outbound webhook — `newBooking`

- **Trigger:** immediately after a successful `POST /bookings` (creation, per the spec's wording — not payment).
- **Payload:** exactly the 201 response body (booking + `links.self`), `Content-Type: application/json`.
- **Target:** the configured webhook URL (default: the Webhook Receiver mock).
- **Delivery:** fire-and-forget — 3s connect/read timeout; on failure log a warning and continue. Retries: none in v1.
- **No response contract is enforced** on the receiver beyond HTTP reachability (the spec documents the
  receiver returning 200).