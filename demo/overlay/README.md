# Train Travel Microservices — gateway

A Spring Boot (Kotlin) gateway implementing the [Train Travel API](train-travel-openapi.yaml), backed by
four external microservices, with a bundled SolidJS UI that consumes the API end-to-end:

| Downstream | Flavour | Contract | Gateway config property |
|---|---|---|---|
| European Rail Reference Data (stations) | REST | [`contracts/stations-openapi.json`](contracts/stations-openapi.json) | `traintravel.stations.base-url` |
| European Rail Timetable (trips) | REST | [`contracts/trips-openapi.json`](contracts/trips-openapi.json) | `traintravel.trips.base-url` |
| Rail Reservations (bookings) | REST | [`contracts/bookings-openapi.json`](contracts/bookings-openapi.json) | `traintravel.bookings.base-url` |
| PayRail (payments) | **SOAP 1.1** | [`contracts/payrail.wsdl`](contracts/payrail.wsdl) + [`payrail.xsd`](contracts/payrail.xsd) | `traintravel.payments.base-url` |
| Google IdP | OAuth2 / OIDC | — | `spring.security.oauth2.resourceserver.jwt.issuer-uri` |
| Webhook receiver | REST sink (`POST`, `newBooking` payload) | — | `traintravel.webhook.url` |

## Prerequisites

- Java 21+ (the Gradle wrapper needs Java 24 or lower to *run*; compilation targets a Java 21 toolchain)
- Reachable instances of the downstream services above
- Node 20+ with [pnpm](https://pnpm.io) — only if you want the UI and/or the browser E2E

## Run it (gateway)

```bash
./gradlew bootRun
```

The gateway starts on `http://localhost:8080`. All downstream base URLs are config properties —
see [`src/main/resources/application.yml`](src/main/resources/application.yml).

## The UI app

A SolidJS + Vite single-page app ([`ui/`](ui/)) that talks to the gateway through the
APIMatic-generated `@wiremock-inc/apimatic-sdkgen-demo` SDK.

With the gateway already running on `localhost:8080`:

```bash
cd ui
pnpm install
pnpm dev          # http://localhost:3000
```

The app defaults to `http://localhost:8080` for its API host; the navbar's "Override API host" input
points it elsewhere, and **Reset** returns to the default.

## Browser E2E

[`e2e/journey.mjs`](e2e/journey.mjs) drives a real Chrome (via `puppeteer-core`) through the whole
journey: log in → find trips → book → declined card → approved payment → My bookings → delete →
log out.

```bash
cd e2e
pnpm install
node journey.mjs
```

The browser window is **visible by default**. Pass `--headless` to run without a window (e.g. in CI).

Requires the gateway on `:8080`, the UI dev server on `:3000`, and Chrome installed at the standard
location.

## Postman

Import both files from [`postman/`](postman/): `train-travel.postman_environment.json` (select it)
and `train-travel.postman_collection.json`.

## Project layout

```
src/main/kotlin/com/wiremock/traintravel/
  config/      AppProperties, SecurityConfig (JWT resource server), RestClientFactory, SoapClientConfig
  common/      Problem (RFC 7807), exception mapping, UUID v5 derivation, pagination helpers
  stations/    stations     → Stations Service
  trips/       trips        → Trips Service
  bookings/    bookings     → Bookings Service
  payments/    payment saga → PayRail SOAP gateway
  webhook/     newBooking outbound webhook
contracts/     downstream API contracts: OpenAPI descriptions + PayRail WSDL/XSD
ui/            SolidJS UI (Vite, :3000)
e2e/           Puppeteer browser journey against the running stack
postman/       collection + environment
```
