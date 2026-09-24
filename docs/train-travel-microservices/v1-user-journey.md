# v1 — User Journey

Written from the perspective of the **demo operator** (the person running the gateway and walking the API
with curl or Postman). Every flow below is scriptable; the curl forms are in the demo README, the Postman
equivalents in the collection.

## Prerequisites

- Gateway running locally on `http://localhost:8080` (`./gradlew bootRun`).
- All six WireMock Cloud mock APIs reachable (their base URLs are gateway config; defaults point at the mock
  estate created during setup).

## Happy path (end-to-end)

### 1. Log in with the mocked Google IdP

1. The operator opens the authorization URL in a browser (or POSTs the login form directly, which the demo
   script does for scriptability):

   ```
   GET {auth-base}/o/oauth2/v2/auth?client_id=train-travel-demo
       &redirect_uri=http://localhost:9090/callback
       &scope=openid email read write
       &state=demo-state
   ```

2. A Google-styled **mock** login page appears. The operator enters any email and name and clicks *Continue*.
3. The browser is redirected to `redirect_uri` with a `code` parameter.
4. The script exchanges the code: `POST {auth-base}/token` with the code. The response now contains an
   **RS256 JWT access token** whose `scope` claim reflects the requested scopes (`openid email read write`).
5. From here on, the script calls the public API with `Authorization: Bearer <jwt>`.

**Data-ready state:** a JWT with `read` + `write` scopes. A token requested with only `openid email read`
produces a **read-only** token, used in the failure flows below.

### 2. Find stations

```
GET http://localhost:8080/stations?search=Milano
```

Returns all stations matching "Milano" (name or address, case-insensitive), paginated (`page`/`limit`,
defaults 1/10, limit max 100), sorted by name ascending, each with a stable UUID `id`, `name`, `address`,
`country_code`, `timezone`, and `links.self` / `links.next` / `links.prev` where applicable.

The operator can also filter by `country=IT`, or supply `coordinates=52.5200,13.4050` to get stations
**sorted by distance** from that point instead of by name.

### 3. Find trips

```
GET http://localhost:8080/trips?origin=<station-uuid>&destination=<station-uuid>&date=2026-10-01T09:00:00Z
```

Returns connections between the two stations on that date: departure/arrival times, operator, price,
`bicycles_allowed`, `dogs_allowed`, and `links` to the trip's `self`, `origin` and `destination` stations.
Sorted by departure time ascending. Optional `bicycles=true` / `dogs=true` restrict to trips that allow them.

**Empty state:** a route/date with no connections returns `200` with `"data": []` and a `self` link only —
not an error.

### 4. Create a booking (webhook fires)

```
POST http://localhost:8080/bookings
Authorization: Bearer <read-write-jwt>
{ "trip_id": "<trip-uuid>", "passenger_name": "Jane Smith", "has_bicycle": true, "has_dog": false }
```

Returns `201` with the booking (`id`, `trip_id`, `passenger_name`, `has_bicycle`, `has_dog`, `links.self`).

**Side effects:**
- The booking is stored in the bookings mock's per-user state, with `status: HELD` and
  `expiresAt = creation + 1 hour`.
- The gateway **fires the `newBooking` webhook** (POST of the booking + `links.self`) at the webhook receiver
  mock. The operator can open the receiver mock's request journal in the WireMock Cloud UI and see the
  delivered payload — that journal entry *is* the demo evidence the webhook fired.

### 5. Pay for the booking

```
POST http://localhost:8080/bookings/{bookingId}/payment
{ "amount": 89.50, "currency": "eur",
  "source": { "object": "card", "name": "J. Doe", "number": "4242424242424242", "cvc": "123",
              "exp_month": 12, "exp_year": 2027, "address_country": "gb", "address_post_code": "N12 9XX" } }
```

Returns `200` with the payment (`id`, `amount`, `currency`, masked `source.number` — `************4242`,
`status: "succeeded"`, `links.booking`). Write-only fields (`cvc`, `address_line1`, `address_line2`) never
appear in responses.

**Side effect:** the booking is now `CONFIRMED`, which is observable: **confirmed bookings never expire**,
whereas unpaid `HELD` bookings vanish after their `expiresAt` (flow 4 below).

### 6. List and manage bookings

```
GET    http://localhost:8080/bookings        ← only the authenticated user's bookings (from JWT sub)
GET    http://localhost:8080/bookings/{id}   ← details, or 404 if unknown/foreign/expired-unpaid
DELETE http://localhost:8080/bookings/{id}   ← 204, releases the hold
```

Bookings are listed in creation order, paginated gateway-side.

## Non-happy-path flows

| # | Flow | Steps | Expected result |
|---|------|-------|-----------------|
| 1 | **No token** | Call any endpoint without `Authorization` | `401`, `application/problem+json` (`https://example.com/errors/unauthorized`), `WWW-Authenticate: Bearer` header |
| 2 | **Invalid/expired token** | Call with garbage bearer token, or one signed by another key, or past `exp` | `401` as above — validation happens locally in the gateway against the IdP mock's JWKS |
| 3 | **Read-only token tries a write** | Log in with scope `openid email read` (no `write`), then `POST /bookings` or `DELETE /bookings/{id}` or `POST .../payment` | `403` (`https://example.com/errors/forbidden`) |
| 4 | **Unpaid booking expires** | Create a booking, don't pay, wait past `expiresAt` (1 hour) — or simulate by adjusting the stored state | `GET`/`DELETE`/payment all return `404`. A **paid** (CONFIRMED) booking still returns `200` after the same time — this is the visible difference payment makes |
| 5 | **Payment declined** | Pay with card number `4000000000000002` | `400` `Problem` with a decline detail (e.g. *"Payment was declined by the card issuer (card_declined)"*). Booking remains `HELD`, still payable with a good card |
| 6 | **Double payment** | Pay an already-CONFIRMED booking again | `400` `Problem` — *"Booking has already been paid"* |
| 7 | **Trips date in the past** | `GET /trips` with `date` before now | `400` `Problem` — *"The search date specified is in the past"* |
| 8 | **Unknown station** | `GET /trips` with a `origin`/`destination` UUID that doesn't resolve to a station | `400` `Problem` — unknown origin/destination station |
| 9 | **Bad query parameters** | e.g. `limit=0`, `limit=500`, `page=0`, malformed `coordinates`, missing required trip params, invalid payment currency/amount/source | `400` `Problem` with a field-specific detail |
| 10 | **Booking not found / foreign** | `GET /bookings/{id}` for an id that doesn't exist, belongs to another user (per-user state isolation), or was deleted | `404` (`https://example.com/errors/not-found`) |
| 11 | **Downstream failure** | Toggle a chaos setting (random HTTP error) on any domain mock in the WireMock Cloud UI mid-demo, then call the matching public endpoint | `500` `Problem` from the gateway; toggle chaos off and the same call succeeds |
| 12 | **Webhook receiver down** | Point the webhook URL config at an unreachable host, create a booking | Booking creation **still returns 201**; gateway logs a warning (fire-and-forget) |
| 13 | **Empty search results** | `GET /stations?search=xyzzy` | `200`, `"data": []`, `self` link only |
| 14 | **SOAP validation fault** | (Chaos/manual) PayRail returns a SOAP fault | Gateway maps to `400` `Problem` |

## Success criteria (what a tester verifies)

1. **Auth:** both the browser-driven login and the scriptable `POST /login` flow yield a JWT that the gateway
   accepts; a tampered token is rejected with 401; a read-only token gets 403 on writes.
2. **Journey:** the six happy-path steps succeed in order, with correct field names and shapes per
   `v1-public-api.md` (spot-check: station/trip UUIDs are stable across repeated calls).
3. **Webhook:** after step 4, the receiver mock's journal in WireMock Cloud shows a POST with the booking payload.
4. **Saga:** the decline card (`4000…0002`) returns 400 and leaves the booking payable; paying then with
   `4242…4242` confirms it.
5. **Expiry:** an unpaid booking 404s after expiry; a paid one doesn't.
6. **Isolation:** user A's token cannot GET, DELETE, or list user B's bookings (404/absent respectively).
7. **Chaos:** toggling a mock's chaos setting surfaces as a clean 500 from the gateway, never a stack trace.
8. **Scripts:** the README curl script runs green top-to-bottom; the Postman collection's folder-level runner
   walks the same journey including token capture.