# Train Travel Microservices — demo gateway

A Spring Boot (Kotlin) gateway implementing the [Train Travel API](train-travel-openapi.yaml), backed by
**four invented external microservices — all of them mocked in WireMock Cloud**:

| Downstream | Flavour | WireMock Cloud mock API | Techniques showcased |
|---|---|---|---|
| European Rail Reference Data | REST | `Train Travel Stations Service` (`wm-train-travel-stations`) | CSV data-driven stubs (`data-exists`), pagination |
| European Rail Timetable | REST | `Train Travel Trips Service` (`wm-train-travel-trips`) | CSV data-driven + date-stamped response templating |
| Rail Reservations | REST | `Train Travel Bookings Service` (`wm-train-travel-bookings`) | Stateful stubs: KV store, per-user state contexts (`X-User-Id`), `require-state` matching |
| PayRail | **SOAP 1.1** | `Train Travel Payments Service` (`wm-train-travel-payments`) | Unstructured mock, body-matched SOAP stubs, masked-card templating |
| Mock Google IdP | OAuth2 | Google OAuth2 template (`5e5y3`) | Auth-code flow, JWKS, **JWT access tokens with `read`/`write` scopes** |
| Webhook receiver | REST sink | `Train Travel Webhook Receiver` (`wm-train-travel-webhook`) | Open-URL stub, request journal as webhook evidence |

Full design specs: [`docs/train-travel-microservices/`](docs/train-travel-microservices/). The mock estate
definitions live in [`mock-estate/`](mock-estate/) (OpenAPI descriptions + stub mapping sets).

## Prerequisites

- Java 21+ (the Gradle wrapper needs Java 24 or lower to *run*; compilation targets a Java 21 toolchain)
- The WireMock Cloud mock estate above (already deployed — the IDs above are live)

## Run it

```bash
./gradlew bootRun
```

The gateway starts on `http://localhost:8080`. All downstream base URLs are config properties —
see [`src/main/resources/application.yml`](src/main/resources/application.yml).

> **Note on the IdP:** the gateway validates JWTs against the mock IdP at
> `https://5e5y3.wiremockapi.cloud`, and the IdP stamps the token's `iss` from whatever base URL issued it.
> Get your tokens from that exact URL (as the script below does) or adjust
> `spring.security.oauth2.resourceserver.jwt.issuer-uri` to match.

## The demo walkthrough (curl)

Every step below is copy-pasteable. The auth flow is fully scriptable — the mock IdP's login is just a
form POST, so no browser is needed (open `https://5e5y3.wiremockapi.cloud/o/oauth2/v2/auth?...` in a
browser if you want the Google-styled page experience).

### 0. Log in (authorization code flow, scripted)

```bash
AUTH=https://5e5y3.wiremockapi.cloud
API=http://localhost:8080

# "Log in" as demo@example.com with read+write scopes - the mock returns a 302 carrying the auth code
LOCATION=$(curl -s -o /dev/null -D - -X POST "$AUTH/login" \
  --data-urlencode "clientId=train-travel-demo" \
  --data-urlencode "email=demo@example.com" \
  --data-urlencode "scopes=openid email read write" \
  --data-urlencode "state=demo-state" \
  --data-urlencode "redirectUri=http://localhost:9090/callback" \
  | grep -i '^location:' | tr -d '\r')
CODE=$(echo "$LOCATION" | sed -E 's/.*[?&]code=([^&]+).*/\1/')

# Exchange the code for tokens - the access token is a real RS256 JWT carrying the requested scopes
TOKEN=$(curl -s -X POST "$AUTH/token" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=$CODE" | jq -r .access_token)
```

A **read-only** token (for the 403 demo): repeat with `scopes=openid email read`.

### 1. Find stations

```bash
curl -s "$API/stations?search=Milano" -H "Authorization: Bearer $TOKEN" | jq .
curl -s "$API/stations?country=DE" -H "Authorization: Bearer $TOKEN" | jq '.data[].name'
curl -s "$API/stations?coordinates=48.85,2.35" -H "Authorization: Bearer $TOKEN" | jq '.data[0]'  # sorted by distance from Paris
```

### 2. Find trips (Berlin → München)

```bash
BERLIN=$(curl -s "$API/stations?search=Berlin" -H "Authorization: Bearer $TOKEN" | jq -r '.data[0].id')
# non-ASCII search terms need URL-encoding -- Postman and browsers do this automatically
MUNICH=$(curl -s --get "$API/stations" --data-urlencode "search=München" -H "Authorization: Bearer $TOKEN" | jq -r '.data[0].id')

curl -s "$API/trips?origin=$BERLIN&destination=$MUNICH&date=$(date -u +%Y-%m-%d)" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

### 3. Create a booking (the webhook fires)

```bash
TRIP=$(curl -s "$API/trips?origin=$BERLIN&destination=$MUNICH&date=$(date -u +%Y-%m-%d)" \
  -H "Authorization: Bearer $TOKEN" | jq -r '.data[0].id')

BOOKING=$(curl -s -X POST "$API/bookings" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"trip_id\": \"$TRIP\", \"passenger_name\": \"Jane Smith\", \"has_bicycle\": true, \"has_dog\": false}")
echo "$BOOKING" | jq .
BOOKING_ID=$(echo "$BOOKING" | jq -r .id)
```

**Webhook evidence:** open the `Train Travel Webhook Receiver` mock API in the WireMock Cloud UI and look at
its request journal — the `newBooking` payload is sitting there.

### 4. Pay for the booking (SOAP saga)

```bash
# Approved (4242...) - the booking becomes CONFIRMED and survives its 1-hour expiry
curl -s -X POST "$API/bookings/$BOOKING_ID/payment" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount": 89.90, "currency": "eur",
       "source": {"object": "card", "name": "J. Doe", "number": "4242424242424242",
                  "cvc": "123", "exp_month": 12, "exp_year": 2027,
                  "address_country": "gb", "address_post_code": "N12 9XX"}}' | jq .
```

Declined card (`4000000000000002`) → `400` with `problem+json`, booking stays `HELD` and can be paid again:

```bash
curl -s -X POST "$API/bookings/$BOOKING_ID/payment" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount": 89.90, "currency": "eur",
       "source": {"object": "card", "name": "J. Doe", "number": "4000000000000002",
                  "cvc": "123", "exp_month": 12, "exp_year": 2027, "address_country": "gb"}}' | jq .
```

Bank accounts work too (`"object": "bank_account"`, with `sort_code` etc.) and are always approved.

### 5. Manage bookings

```bash
curl -s "$API/bookings" -H "Authorization: Bearer $TOKEN" | jq .          # your bookings only
curl -s "$API/bookings/$BOOKING_ID" -H "Authorization: Bearer $TOKEN" | jq .
curl -s -X DELETE "$API/bookings/$BOOKING_ID" -H "Authorization: Bearer $TOKEN" -o /dev/null -w '%{http_code}\n'   # 204
```

### Failure flows to demo

| Flow | How |
|---|---|
| 401 | Drop the `Authorization` header |
| 403 | Use the read-only token on any write (`POST /bookings` …) |
| 400 past date | `date=2020-01-01` on `/trips` |
| 400 unknown station | made-up UUID for `origin` on `/trips` |
| 400 already paid | pay a CONFIRMED booking again |
| 404 expiry | create a booking, don't pay, wait an hour (or edit the state's `expiresAt` in WireMock Cloud) — unpaid holds vanish; paid ones don't |
| 500 chaos | toggle a random-failure chaos setting on any domain mock in the WireMock Cloud UI, call the matching endpoint, toggle it off |
| user isolation | get a token with a different `email`, then try to `GET` the first user's booking → 404 |

## Postman

Import both files from [`postman/`](postman/):

1. `train-travel.postman_environment.json` (select it)
2. `train-travel.postman_collection.json`

The collection's first two requests script the same login → token exchange (capturing `access_token`
automatically); every later request sends it as a bearer token. Run the collection top to bottom to walk
the whole journey.

## Resetting the demo

Bookings accumulate in the bookings mock's per-user state contexts. In WireMock Cloud open
`Train Travel Bookings Service` → **Reset state**, or via MCP: `reset_state` on `vkoeq`. The webhook
receiver's journal can be cleared with `reset_request_journal` on `o9wy0`.

## Project layout

```
src/main/kotlin/com/wiremock/traintravel/
  config/      AppProperties, SecurityConfig (JWT resource server), RestClientFactory, SoapClientConfig
  common/      Problem (RFC 7807), exception mapping, UUID v5 derivation, pagination helpers
  stations/    stations   → Stations Service (CSV data-driven mock)
  trips/       trips      → Trips Service (CSV data-driven mock)
  bookings/    bookings   → Bookings Service (stateful mock, per-user X-User-Id state)
  payments/    payment saga → PayRail SOAP gateway (unstructured mock)
  webhook/     newBooking outbound webhook → Webhook Receiver mock
src/main/resources/payrail.wsdl      contract-first WSDL; JAXB classes generated at build time
mock-estate/                         OpenAPI descriptions + stub sets for the WireMock Cloud mocks
docs/train-travel-microservices/     full specification documents
postman/                             collection + environment
```