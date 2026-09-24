# Architecture — Integration Map

The single source of truth for implementation. A ticket author should never need to explore the codebase or
the WireMock Cloud account to fill in gaps — everything non-obvious is here. Companion documents:
[`v1-public-api.md`](v1-public-api.md) (per-endpoint behaviour), [`v1-downstream-services.md`](v1-downstream-services.md)
(downstream contracts & mock strategies), [`v1-user-journey.md`](v1-user-journey.md) (flows).

## 1. System shape

```
                                ┌────────────────────────────────────────────────┐
 Client (curl / Postman) ───────►  Spring Boot gateway  :8080                    │
   Bearer JWT (RS256)            │  Kotlin · Java 21 · Boot 3.5.x · servlet     │
                                │  stateless — no database                       │
                                └──┬─────────┬─────────┬─────────┬─────────┬────┘
                  JWKS on startup   │         │         │         │         │  POST webhook
                  (auth mock)        ▼         ▼         ▼         ▼         ▼
                             Stations    Trips     Bookings    PayRail   Webhook
                             Service     Service   Service     (SOAP)    Receiver
                             REST+CSV    REST+CSV  REST+state REST      REST sink
                             (openapi)   (openapi) (openapi)  (unstr.)  (unstr.)
                                   └───────── all are WireMock Cloud mock APIs ─┘
 Auth mock (Google OAuth2 template, id 5e5y3): browser/script login → code → JWT access token
```

**The gateway is stateless.** There is **no database and no repository layer** — nothing to persist. The only
"state" is (a) the downstream mock estate and (b) two pure functions:

- `stationUuid(stationCode) = UUID v5(namespace, stationCode)`
- `tripUuid(fromCode, toCode, scheduledDepartureIso) = UUID v5(namespace, "$from|$to|$dep")`

Both use one fixed namespace UUID constant defined once in code (e.g. `UUID.fromString("…")` in
`Uuids.kt`). Because they are pure, UUIDs survive restarts and are identical across clients — **no cache
or reverse map is needed or wanted** (bookings store the trip UUID opaquely; that's why reverse mapping
never happens — see §6).

## 2. Build & runtime

| Concern | Decision |
|---|---|
| Language | Kotlin (no coroutines — blocking style throughout; servlet stack, Tomcat) |
| JVM | Java 21 toolchain (wrapper is Gradle 9.7.1, which runs on modern JDKs) |
| Spring Boot | 3.5.16 |
| Build | Gradle 9.7.1, Kotlin DSL; Kotlin plugin 2.4.20 |
| HTTP client (REST downstream) | `RestClient` (Spring 6.1+) — one bean per downstream, configured from properties, connect timeout 5s, read timeout 10s |
| SOAP client | Spring WS `WebServiceTemplate`; JAXB classes generated from `payrail.wsdl` (see §7 codegen) |
| Security | `spring-boot-starter-oauth2-resource-server` — local JWT validation |
| JSON | Jackson with `PropertyNamingStrategies.SNAKE_CASE` module-wide (public API is snake_case; downstream clients opt out per-mapper — see §5) |
| Validation | `spring-boot-starter-validation` + bean validation on request DTOs |
| Tests | JUnit 5 + MockMvc (`@WebMvcTest` slice per controller with downstream clients mocked) + `springmockk`; one full-journey `@SpringBootTest` against static WireMock stub JSON if desired |

**Gradle dependencies (complete):** `spring-boot-starter-web`, `spring-boot-starter-validation`,
`spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-web-services` (client classes),
`jackson-module-kotlin`, dev tools as needed; test: `spring-boot-starter-test`, `springmockk`.
Plus the CXF wsdl2java toolchain for codegen (§7).

## 3. Configuration properties (complete)

```yaml
server:
  port: 8080

spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${AUTH_BASE_URL}/oauth2/v3/certs
          issuer-uri: ${AUTH_BASE_URL}

traintravel:
  public-base-url: http://localhost:8080      # used to compose all links.* URLs
  auth-base-url: https://wm-google-….5e5y3….wiremockapi.cloud   # IdP mock (jwk-set-uri/issuer derive from this)
  stations:
    base-url: https://wm-train-travel-stations-<id>.wiremockapi.cloud
  trips:
    base-url: https://wm-train-travel-trips-<id>.wiremockapi.cloud
  bookings:
    base-url: https://wm-train-travel-bookings-<id>.wiremockapi.cloud
  payments:
    base-url: https://wm-train-travel-payments-<id>.wiremockapi.cloud/payrail/v1
  webhook:
    url: https://wm-train-travel-webhook-<id>.wiremockapi.cloud/new-booking
    timeout: 3s
  downstream:
    connect-timeout: 5s
    read-timeout: 10s
```

`<id>` suffixes are the WireMock Cloud mock API IDs assigned at creation — fill in after the mock estate is
built. `AUTH_BASE_URL` also backs the demo login script URLs in the README (the login/token endpoints are on
the auth mock, not the gateway).

## 4. Layer-by-layer responsibilities

| Layer | Produces | Consumes |
|---|---|---|
| `@RestController` (one per domain: `StationsController`, `TripsController`, `BookingsController`, `PaymentsController`) | Public DTOs (`StationDto`, `TripDto`, `BookingDto`, `BookingPaymentDto`, `CollectionDto<T>`) with `links` composed from `public-base-url`; HTTP status mapping | Domain services |
| Domain services (`StationsService`, `TripsService`, `BookingsService`, `PaymentSaga`, `WebhookNotifier`) | Translation between public DTOs and downstream models; validation; saga orchestration; expiry enforcement; UUID derivation | Downstream clients |
| Downstream clients (`StationsClient`, `TripsClient`, `BookingsClient` — RestClient; `PayRailClient` — WebServiceTemplate) | Typed downstream request/response models (Kotlin data classes, separate per service — NOT the public DTOs) | WireMock Cloud mock APIs |
| `SecurityConfig` | Resource-server filter chain, scope authorities | JWKS from auth mock |
| `ApiExceptionHandler` (`@RestControllerAdvice`) | RFC 7807 `Problem` for every non-2xx | exceptions from all layers |

**Package layout** (single Gradle module):

```
src/main/kotlin/com/wiremock/traintravel/
  TrainTravelApplication.kt
  config/        SecurityConfig.kt, RestClientConfig.kt, SoapClientConfig.kt, WebhookConfig.kt
  stations/      StationsController.kt, StationsService.kt, StationsClient.kt, StationDto.kt
  trips/         TripsController.kt, TripsService.kt, TripsClient.kt, TripDto.kt
  bookings/      BookingsController.kt, BookingsService.kt, BookingsClient.kt, BookingDto.kt
  payments/      PaymentsController.kt, PaymentSaga.kt, PayRailClient.kt, BookingPaymentDto.kt
  webhook/       WebhookNotifier.kt
  common/        Problem.kt, ApiExceptionHandler.kt, Uuids.kt, CollectionDto.kt
src/main/resources/payrail.wsdl     (hand-authored — see v1-downstream-services.md §4)
build/generated/wsdl/               (JAXB output — see §7)
```

## 5. Non-obvious framework patterns (the things an implementer must not re-derive)

1. **Scope authorities:** Spring's default `JwtGrantedAuthoritiesConverter` maps a space-separated `scope`
   claim to `SCOPE_x` authorities. Our extended Token stub emits `scope: "openid email read write"`, so
   method security is simply:
   - GETs: `@PreAuthorize("hasAuthority('SCOPE_read')")`
   - writes: `@PreAuthorize("hasAuthority('SCOPE_write')")`
   Do not build a custom converter.
2. **Issuer validation:** validate `iss == auth-base-url` (Spring does this from `issuer-uri`). Do **not**
   validate `aud` (the token's `aud` is the OAuth client id, not a resource indicator) — leave audience
   validation off.
3. **401 vs 403:** 401 comes from the filter chain (bad/missing token — include `WWW-Authenticate: Bearer`);
   403 comes from method security. Both must render `Problem` via `AuthenticationEntryPoint` /
   `AccessDeniedHandler` wired in `SecurityConfig` (the default Spring ones return empty bodies — override).
4. **Two Jackson naming regimes:** public DTOs are snake_case (`trip_id`) — set
   `spring.jackson.property-naming-strategy: SNAKE_CASE` globally. Downstream models use their own
   conventions (`stationName`, `passengerName`). Since each downstream client has its own data classes,
   annotate the *downstream* models with `@JsonProperty`/`@JsonNaming(UPPER_CAMEL_CASE)` explicitly, and let
   the global snake_case apply to public DTOs. (Downstream JSON payloads are small; per-model annotations
   are cheaper than per-client mappers.)
5. **RestClient error handling:** every client sets `.defaultStatusHandler` to throw a
   `DownstreamException(status, body)` for any non-2xx it doesn't explicitly handle, so
   `ApiExceptionHandler` can map downstream 4xx/5xx/timeouts to a public 500 (`Problem`,
   `…/errors/internal-server-error`). Never surface a downstream body verbatim — it's not the public schema.
6. **404 vs downstream 404 (bookings):** `BookingsClient` treats downstream 404 as a *value*
   (`null`/`BookingNotFound`), not an error — the gateway then applies ownership/expiry logic and decides the
   public 404 itself.
7. **SOAP:** `WebServiceTemplate.marshalSendAndReceive` with the JAXB-generated request factory; set
   `SOAPAction: "processPayment"` via a `ClientInterceptor` or action property; `Content-Type: text/xml`.
   PayRail faults arrive as `SoapFaultException` — catch, map `faultCode=VALIDATION` → public 400,
   anything else → 500. Response `DECLINED` is **not** an exception — it's a normal 200 SOAP response the
   saga inspects.
8. **Webhook fire-and-forget:** `WebhookNotifier` runs on the same request thread *after* the booking is
   created but must never fail the 201 — wrap in try/catch, log warn on any failure, 3s timeout. Do not use
   `@Async` (avoid coroutine/async complexity; the call is ~10ms against a mock).
9. **Expiry rule in one place:** implement `isExpired(reservation) = status == "HELD" && expiresAt < now`
   once in `BookingsService`; apply it in GET, DELETE, list (filter expired HELD out of listings too), and
   payment pre-validation. Confirmed bookings never expire.
10. **Pagination composition:** `links.next` exists iff `(page * limit) < total`; `links.prev` iff
    `page > 1`. Build URLs from `public-base-url` + the original query string (URL-encode params — the date
    contains `:`).

## 6. Data flows (with volumes)

All payloads are demo-scale: stations CSV ≈ 20 rows; connections CSV ≈ 64 rows; booking/payment bodies
< 2KB; SOAP envelope < 4KB; webhook payload < 1KB. **No volume-driven design constraints** — in-memory
processing is always correct; nothing needs streaming, batching, or shared identifiers passed out of band.

| Flow | Steps |
|---|---|
| `GET /stations` | Resolve `country` (upper) → `GET {stations}/station-data?country=…&pageSize=100` → gateway: search-substring filter (name or address) → distance sort if `coordinates` (haversine; parse `"<lat>,<lon>"`, invalid → 400) else name sort → paginate in memory → map to `Station[]` (UUID v5) → compose links |
| `GET /trips` | Validate required params; past date → 400; resolve `origin`/`destination` UUID → stationCode (fetch stations, look up by v5 id; miss → 400) → `GET {trips}/connections?from=…&to=…&date=YYYY-MM-DD&page=…&pageSize=limit` → gateway filters bicycles/dogs (safe: ≤4 rows per route < page size) → map to `Trip[]` (UUID v5) → compose links |
| `POST /bookings` | Validate body → `POST {bookings}/reservations` (X-User-Id header = JWT sub; body tripRef/passengerName/bicycle/dog) → 201 → map to `Booking` → **fire webhook** (POST 201-body to `webhook.url`, try/catch warn) → respond 201 |
| `GET /bookings` | `GET {bookings}/reservations` (X-User-Id) → filter out expired-HELD → creation order → gateway-side page slice → map |
| `GET /bookings/{id}` | `GET {bookings}/reservations/{id}` (X-User-Id; per-user context means foreign ids are simply absent → 404) → expired-HELD → 404 → map |
| `DELETE /bookings/{id}` | Same pre-checks → `DELETE {bookings}/reservations/{id}` → 204 |
| `POST /bookings/{id}/payment` | Get reservation (404/expired/ownership) → already CONFIRMED → 400 → validate body → `ProcessPayment` SOAP (merchantRef=bookingId, amount, currency upper-cased, source mapped) → DECLINED → 400 `Problem` with decline reason → APPROVED → `POST {bookings}/reservations/{id}/confirm` → map transactionId/masked number to `BookingPayment`, `status: succeeded` → 200 |

## 7. Codegen & sync steps (after changes)

| Step | Trigger | Command |
|---|---|---|
| Generate SOAP client | After any `payrail.xsd` edit | `./gradlew generateSoapClient` — a `JavaExec` task invoking `com.sun.xml.bind:jaxb-xjc` (`XJCFacade`) on `src/main/resources/payrail.xsd` (`-d build/generated/wsdl`, `-p com.wiremock.traintravel.payrail.generated`). `xjc` reads schemas, not WSDLs — that is why the schema lives in `payrail.xsd`, imported by `payrail.wsdl` via `xsd:import` (single source of truth). Output is **generated at build, never committed** |
| Kotlin build | Always | `./gradlew build` (JAXB generation is a dependency of `compileKotlin`) |
| Mock estate | After stub/data changes | Created/updated via the WireMock Cloud MCP tools during the build phase (§8); OpenAPI descriptions for the three openapi-type mocks are pushed via the `push` tool (`type: openapi_description`) whenever their contracts change |

**There is no API type-sync codegen for the public API** — the gateway hand-implements
`train-travel-openapi.yaml` (it is the authority for shapes; DTOs mirror it directly). No frontend exists.

## 8. Mock estate (WireMock Cloud) — creation checklist

| # | Mock API | Type | Hostname | Created via |
|---|---|---|---|---|
| 1 | Google OAuth2 template | template | `wm-…` (existing) | **Done by user in UI** — id `5e5y3` |
| 2 | Train Travel Stations Service | openapi | `wm-train-travel-stations` | MCP: `create_mock_api` → author OpenAPI → `push` (openapi_description) → `create_data_source` (CSV) → `import_stubs_to_mock_api` |
| 3 | Train Travel Trips Service | openapi | `wm-train-travel-trips` | as above |
| 4 | Train Travel Bookings Service | openapi | `wm-train-travel-bookings` | MCP + `update_mock_api_settings` (state: context expression `reservations:{{request.headers.x-user-id}}`) |
| 5 | Train Travel Payments Service (PayRail) | unstructured | `wm-train-travel-payments` | MCP: `create_mock_api` (type unstructured) → `import_stubs_to_mock_api` (the 4–5 SOAP stubs) |
| 6 | Train Travel Webhook Receiver | unstructured | `wm-train-travel-webhook` | MCP: one open-URL POST stub |

Plus: **Token stub extension on `5e5y3`** via `update_stub_mapping` (scopes in login code → JWT access
token; exact template changes in `v1-downstream-services.md` §5).

Data source CSVs: `Train Stations` (~20 rows) and `Train Connections` (~64 rows) — column lists in
`v1-downstream-services.md`.

**Reset/lifecycle:** bookings state accumulates per user; reset between demos via the mock API's
*Reset state* action (or MCP `reset_state` on `wm-train-travel-bookings`). Request journals on the webhook
receiver are the demo's webhook evidence; clear via `reset_request_journal` when needed.

## 9. Third-party constraints affecting the design

| Dependency | Constraint | Consequence |
|---|---|---|
| WireMock Cloud state store | Values are JSON docs in contexts; keys listed via `listState` in **insertion order**; no querying across keys | Per-user listing needs per-user contexts (context expression); duplicate-detection on create is impossible → 409 cut; listing order = creation order (documented) |
| WireMock Cloud `data-exists` matcher | SQL-like filter over CSV columns; ORDER BY/LIMIT/OFFSET supported (proven in `j0meq`); no geo/text-search functions | Country + route + pagination live in the mock; text search and proximity live in the gateway |
| WireMock Cloud templating | `jwt` helper signs RS256 with the mock's JWKS key; `randomValue type='UUID'`; `now`; `date offset=…`; `parseDate` | Token stub extension and PayRail stubs rely on these; booking `expiresAt` computed via `date offset` |
| Google OAuth2 template | Access token is opaque by default; scopes openid/email/profile vocabulary; login page passes `scope` through as a form field | Token + login-handler stub extension required (§5 of downstream doc); custom `read`/`write` scopes are simply passed through the `scope` string — the template doesn't validate the vocabulary |
| Unstructured mock type | No OpenAPI validation; stubs match on method/URL/body patterns; bodies are raw templates | SOAP matching via body `contains`/XPath; **stub priority ordering matters** (specific card stubs must outrank catch-all) |
| Hostname rules | Unqualified, lowercase, hyphens; user convention: `wm-` prefix | All hostnames as listed in §8 |
| SOAP 1.1 / Spring WS | `text/xml` content type + `SOAPAction` header; JAXB requires generated classes from WSDL | Contract-first WSDL in `src/main/resources`, CXF codegen task |

## 10. Lint / code-style constraints

- **Greenfield standalone repo** — no existing lint config. The Tessl monorepo's lint rules (loop-callback
  object construction, route registration in `apps/backend/src/routes/v1/index.ts`, `bun run api:sync`) do
  **not apply here** — this is a Spring/Kotlin project with no auto-registration or type-sync step. Do not
  look for them.
- Establish **ktlint** from the first commit (Gradle `ktlint` plugin, standard style). No bespoke rules.
- Controllers are thin; all translation in domain services; one class per downstream client.

## 11. Error-mapping table (gateway, single source)

| Source | Public result |
|---|---|
| Missing/invalid/expired JWT | 401 `Problem` + `WWW-Authenticate: Bearer` |
| Method-security denial (scope) | 403 `Problem` |
| Bean-validation failure / malformed body / bad query params | 400 `Problem`, detail names the field |
| Past trip date; unresolvable station UUID | 400 `Problem` |
| Booking not found / foreign / expired-HELD (GET, DELETE, payment) | 404 `Problem` |
| Payment DECLINED | 400 `Problem` (detail includes decline reason) |
| PayRail SOAP fault (`VALIDATION`) | 400 `Problem` |
| Downstream non-2xx (other), SOAP fault (other), downstream timeout | 500 `Problem` |
| Webhook delivery failure | **No public error** — warn log; booking creation still 201 |

## 12. Demo artifacts

1. **README (repo root)** — run instructions + a fully scriptable curl walkthrough: direct
   `POST {auth}/login` (form: `clientId=train-travel-demo`, any `email`, `scopes=openid email read write`,
   `state`, `redirectUri=http://localhost:9090/callback`) → extract `code` from the `Location` header →
   `POST {auth}/token` → JWT → the 6-step journey + decline/expiry flows.
2. **Postman collection (repo root)** — OAuth2 helper against the auth mock (Auth URL
   `{auth}/o/oauth2/v2/auth`, Token URL `{auth}/token`, client id `train-travel-demo`, scopes
   `openid email read write`, "Handle browser login" popup fills the mock Google form); folder-level runner
   walks the journey; an environment file carries the mock base URLs.
3. Both scripts demonstrate the same flows as `v1-user-journey.md` §Happy path and §Flows 3, 4, 5.