# v1 — Downstream Services (Invented Contracts & Mock Strategies)

Four invented domain APIs (which don't exist — we're inventing them), one auth mock, one webhook receiver.
Each section gives the complete contract (a ticket author quotes it; no re-research needed) plus the exact
WireMock Cloud mock strategy.

Naming convention: every new mock API's hostname is prefixed `wm-`.

---

## 1. Stations — "European Rail Reference Data"

**Mock API:** `Train Travel Stations Service` · hostname `wm-train-travel-stations` · type **openapi**
(we author a small OpenAPI file and push it as the API description; stubs attached to operations).

### Contract

```
GET /station-data?country=DE&pageSize=100&offset=0
200 →
{
  "stations": [
    {
      "stationCode": "DE-BER-HBF",            // natural key, format: {CC}-{CITY}-{3-LETTER}
      "stationName": "Berlin Hauptbahnhof",
      "addressLines": ["Invalidenstraße 10557", "Berlin", "Germany"],
      "countryIsoCode": "DE",
      "geo": { "latitude": 52.5250, "longitude": 13.3694 },
      "tzId": "Europe/Berlin"
    }
  ],
  "total": 20, "offset": 0, "pageSize": 100
}
```

- `country` optional exact filter (uppercase ISO alpha-2); absent → all stations.
- `pageSize` (1–100, default 10) / `offset` (≥ 0) pagination, ordered by `stationName` ascending.
- Gateway always fetches `pageSize=100` (full dataset ≈ 20 rows) and does **search/proximity/pagination itself**
  — keeps the mock matcher trivial and the query logic in one place.
- Errors: the mock returns plain 400/500 bodies; the gateway never surfaces them verbatim (mapped to `Problem`).

### WireMock Cloud strategy

- **CSV data source** `Train Stations` — ~20 real European stations, columns:
  `stationCode, stationName, addressLine1, addressLine2, addressLine3, countryIsoCode, latitude, longitude, tzId`
  (examples: Berlin Hbf `DE-BER-HBF`, Paris Gare du Nord `FR-PAR-NOT`, Milano Centrale `IT-MIL-CEN`,
  London St Pancras `GB-LON-STP`, Amsterdam Centraal `NL-AMS-CEN`, Zürich HB `CH-ZRH-HBF`, Wien Hbf `AT-VIE-HBF`,
  Bruxelles-Midi `BE-BRU-MID`, Madrid Atocha `ES-MAD-ATO`, Roma Termini `IT-ROM-TRM`, …).
- One stub per operation, using the **`data-exists`** custom matcher with a SQL-style query
  (pattern proven in the existing `j0meq` mock):

  ```
  countryIsoCode='{{request.query.country}}' ORDER BY stationName
  LIMIT {{pageSize}} OFFSET {{offset}}
  ```

  (omit the country clause when the param is absent — two stub variants or a template condition).
- Response via `{{#formatJson}}` + `{{#arrayJoin ',' data.items as |station|}}`, rendering `geo` as a nested
  object from the `latitude`/`longitude` columns, `addressLines` as an array of non-empty address columns.
- **No state** needed. Data-driven only.

### Gateway mapping (public `Station`)

| Downstream | Public |
|---|---|
| `stationCode` | `id` = UUID v5(stationCode) |
| `stationName` | `name` |
| `addressLines` joined with ", " | `address` |
| `countryIsoCode` | `country_code` |
| `tzId` | `timezone` |

---

## 2. Trips — "European Rail Timetable"

**Mock API:** `Train Travel Trips Service` · hostname `wm-train-travel-trips` · type **openapi**.

### Contract

```
GET /connections?from=DE-BER-HBF&to=FR-PAR-NOT&date=2026-10-01&page=1&pageSize=10
200 →
{
  "connections": [
    {
      "connectionId": "ICE-593-0800",
      "from": { "stationCode": "DE-BER-HBF", "scheduledDeparture": "2026-10-01T08:00:00+02:00" },
      "to":   { "stationCode": "FR-PAR-NOT", "scheduledArrival": "2026-10-01T14:30:00+02:00" },
      "operator": { "code": "DB", "name": "Deutsche Bahn" },
      "fare": { "amount": 89.50, "currency": "EUR" },
      "facilities": { "bicyclesAllowed": true, "dogsAllowed": false }
    }
  ],
  "page": 1, "pageSize": 10, "totalElements": 4
}
```

- `from`/`to` required (station codes, resolved by the gateway from public UUIDs); `date` required (ISO date).
- Ordered by `scheduledDeparture` ascending; `page`/`pageSize` pagination downstream.
- The service holds **time-of-day templates**, not dated rows: the requested `date` is stamped on in the
  response template, so any future date returns plausible connections (the trick that keeps the mock small).
- No error responses beyond trivial 400/500 — the gateway owns validation.

### WireMock Cloud strategy

- **CSV data source** `Train Connections` — ~8 major European routes × 4 departures each × both directions
  (~64 rows), columns (mirroring the proven `j0meq` `Train Trips` pattern, domain-renamed):

  ```
  connectionId, fromStationCode, toStationCode, departure_time (HH:mm), duration_minutes,
  operatorCode, operatorName, fare_amount, fare_currency, bicycles_allowed, dogs_allowed
  ```

  Routes: Berlin–München (ICE), Berlin–Paris, Paris–Amsterdam (Eurostar), London–Paris (Eurostar),
  Milano–Roma (Frecciarossa), Amsterdam–Brussels (IC), Paris–Zürich (TGV Lyria), Wien–Budapest (Railjet).
- **`data-exists`** matcher query:

  ```
  fromStationCode='{{request.query.from}}' AND toStationCode='{{request.query.to}}'
  ORDER BY departure_time LIMIT {{pageSize}} OFFSET {{offset}}
  ```

- Response template with `arrayJoin` over `data.items`; `scheduledDeparture` built as
  `{{request.query.date}}T{{connection.departure_time}}:00`; `scheduledArrival` =
  `{{date (parseDate departure) offset=(duration_minutes + ' minutes')}}` (same `parseDate`/`date offset`
  technique as the existing mock, adapted).
- Operator object assembled from the two operator columns; `facilities` nested object from the two boolean columns.
- **No state.**

### Gateway mapping (public `Trip`)

| Downstream | Public |
|---|---|
| UUID v5(from, to, scheduledDeparture) | `id` |
| `from.stationCode` → v5 station UUID | `origin` |
| `to.stationCode` → v5 station UUID | `destination` |
| `scheduledDeparture` / `scheduledArrival` | `departure_time` / `arrival_time` |
| `operator.name` | `operator` |
| `fare.amount` | `price` (currency dropped — documented simplification) |
| `facilities.bicyclesAllowed` / `dogsAllowed` | `bicycles_allowed` / `dogs_allowed` |

Bicycle/dog filtering and the past-date/unknown-station validation happen **gateway-side**.

---

## 3. Bookings — "Rail Reservations"

**Mock API:** `Train Travel Bookings Service` · hostname `wm-train-travel-bookings` · type **openapi**
(stateful stubs attached to a minimal OpenAPI description).

### Contract

The gateway sends `X-User-Id: <jwt-sub>` on every call. **State management setting on this mock API:**
context expression `reservations:{{request.headers.x-user-id}}` — every state operation below is scoped to
that user's bucket, which is what makes listings per-user and cross-user reads impossible.

```
POST /reservations
  body: { "tripRef": "<public trip uuid, opaque>", "passengerName": "Jane Smith",
          "bicycle": true, "dog": false, "holderId": "<jwt sub>" }
  201 → { "reservationId": "<uuid>", "status": "HELD", "expiresAt": "<now+1h ISO8601>",
          "tripRef": …, "passengerName": …, "bicycle": …, "dog": …, "createdAt": "<now>" }

GET /reservations
  200 → { "reservations": [ <reservation objects in creation order> ] }

GET /reservations/{reservationId}
  200 → <reservation object>  |  404 (require-state absent)

POST /reservations/{reservationId}/confirm
  200 → <reservation object with "status": "CONFIRMED">  |  404

DELETE /reservations/{reservationId}
  204  |  404
```

- `tripRef` is stored opaquely — never validated (documented simplification).
- `expiresAt` is computed at creation by the template (`now + 1 hour`) — the mock never enforces it;
  **the gateway enforces expiry** (404 for HELD past `expiresAt`; CONFIRMED never expires).

### WireMock Cloud strategy (KV state store — patterns proven in `j0meq` and WireMock Cloud docs)

- **Create:** `change-state` listener → `REQUEST_VAR id = {{randomValue type='UUID'}}`,
  `REQUEST_VAR collectionContext = reservations:<x-user-id>` (or rely on the mock's context expression),
  then `SET` key `{{id}}` = `{{#jsonMerge request.body removeNulls=true}}` merged with
  `{"reservationId": "{{id}}", "status": "HELD", "createdAt": "{{now}}", "expiresAt": "{{date (parseDate (now)) offset='3600 seconds'}}"}`;
  response body `{{state id context=collectionContext}}`.
- **Get / list:** `require-state` custom matcher (`custom-and-matcher` wrapper, `context: reservations:<…>`,
  `key: {{request.path.reservationId}}`, `absent: true` → 404 stub); body `{{state id context=collectionContext}}`;
  list body `[{{arrayJoin ',' (listState collectionContext)}}]` wrapped in `{"reservations": [...]}`.
- **Confirm:** `require-state` present; listener re-`SET`s the key with `{{#jsonMerge previousValue request.body}}`
  plus `"status": "CONFIRMED"` (use `jsonMerge previousValue` with an override body — merge strategy per docs).
- **Delete:** `require-state` present; listener `DELETE` operation on the key; 204 response.
- **Reset for demos:** `DELETE_CONTEXT` equivalent is available via WireMock Cloud's *Reset state* action on
  the mock API (per-user contexts die with it) — no code required.
- **No CSV.** All data lives in the state store.

### Gateway mapping (public `Booking`)

| Downstream | Public |
|---|---|
| `reservationId` | `id` |
| `tripRef` | `trip_id` |
| `passengerName` | `passenger_name` |
| `bicycle` / `dog` | `has_bicycle` / `has_dog` |
| `status`, `expiresAt`, `createdAt`, `holderId` | internal only — never returned publicly |

---

## 4. Payments — "PayRail" SOAP 1.1 gateway

**Mock API:** `Train Travel Payments Service (PayRail)` · hostname `wm-train-travel-payments` ·
type **unstructured** (no OpenAPI — raw SOAP stubs).

### Contract (the WSDL we hand-author, `payrail.wsdl`)

```
POST /payrail/v1
Content-Type: text/xml; charset=utf-8
SOAPAction: "processPayment"
namespace: http://payrail.example.com/v1
```

**Request:**

```xml
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:pay="http://payrail.example.com/v1">
  <soapenv:Body>
    <pay:processPaymentRequest>
      <pay:merchantId>TRAINTRAVEL-EU</pay:merchantId>
      <pay:merchantReference>1725ff48-ab45-4bb5-9d02-88745177dedb</pay:merchantReference>
      <pay:amount>89.50</pay:amount>
      <pay:currencyCode>EUR</pay:currencyCode>
      <pay:paymentMethod>
        <pay:card>
          <pay:cardholderName>J. Doe</pay:cardholderName>
          <pay:cardNumber>4242424242424242</pay:cardNumber>
          <pay:expiryMonth>12</pay:expiryMonth>
          <pay:expiryYear>27</pay:expiryYear>
          <pay:securityCode>123</pay:securityCode>
          <pay:billingPostCode>N12 9XX</pay:billingPostCode>
        </pay:card>
        <!-- or -->
        <pay:bankAccount>
          <pay:accountName>J. Doe</pay:accountName>
          <pay:accountNumber>00012345</pay:accountNumber>
          <pay:sortCode>000123</pay:sortCode>
          <pay:accountType>individual</pay:accountType>
          <pay:bankName>Starling Bank</pay:bankName>
          <pay:countryCode>GB</pay:countryCode>
        </pay:bankAccount>
      </pay:paymentMethod>
    </pay:processPaymentRequest>
  </soapenv:Body>
</soapenv:Envelope>
```

**Response (approved):**

```xml
<soapenv:Envelope xmlns:soapenv="…" xmlns:pay="http://payrail.example.com/v1">
  <soapenv:Body>
    <pay:processPaymentResponse>
      <pay:transactionId>2e3b4f5a-6b7c-8d9e-0f1a-2b3c4d5e6f7a</pay:transactionId>
      <pay:responseCode>APPROVED</pay:responseCode>
      <pay:authorisedAmount>89.50</pay:authorisedAmount>
      <pay:maskedCardNumber>************4242</pay:maskedCardNumber>
      <pay:processedAt>2026-09-24T12:34:56Z</pay:processedAt>
    </pay:processPaymentResponse>
  </soapenv:Body>
</soapenv:Envelope>
```

Declined: `responseCode` = `DECLINED` + `<pay:declineReason>card_declined</pay:declineReason>`, no
`maskedCardNumber`/`authorisedAmount`. Validation failures: SOAP `soapenv:Fault` with
`<pay:PayRailFault><pay:faultCode>VALIDATION</pay:faultCode><pay:faultText>…</pay:faultText></pay:PayRailFault>`.

**Decision drivers (body-matched stubs):**

| Stub | Match (request body contains) | Response |
|---|---|---|
| Approve card | `4242424242424242` | `APPROVED`, `transactionId={{randomValue type='UUID'}}`, `maskedCardNumber=************4242`, `processedAt={{now}}` |
| Decline card | `4000000000000002` | `DECLINED`, `declineReason=card_declined` |
| Approve bank account | `<pay:sortCode>` | `APPROVED`, `maskedAccountNumber=*********2345` |
| Approve any other card | catch-all `processPaymentRequest` + `<pay:card>` | `APPROVED` with masked number `************` + last 4 via templating |
| Validation fault | (manual/chaos trigger — e.g. currency `XXX`) | SOAP fault |

All responses via `response-template`; the XML envelope is the body template, so helpers
(`randomValue`, `now`, `date`) interpolate directly inside it. Stubs are `persistent: true` with
priorities so the specific-card stubs win over the catch-all.

### Gateway mapping (public `BookingPayment`)

| SOAP | Public |
|---|---|
| `transactionId` | `id` |
| request `amount`/`currencyCode` (upper) | `amount` / `currency` (lower) |
| `maskedCardNumber` / `maskedAccountNumber` | `source.number` |
| cardholder/account name | `source.name` |
| request source fields | `source.*` (write-only fields omitted on read) |
| `APPROVED` → `status: "succeeded"`; `DECLINED`/fault → 400 `Problem` | — |

---

## 5. Auth — Google OAuth2 template mock (existing, `5e5y3`)

**Mock API:** already created from the pre-built Google OAuth2 template. Hostname is `wm-`-prefixed.
**One extension is required** (built via MCP `update_stub_mapping` during the build phase):

**Problem:** the template's `access_token` is opaque (`access..<base64 code>`) — a resource server can't
validate it, and it carries no scopes. Only the `id_token` is a real RS256 JWT.

**Extension (approved in interview):**

1. **Login handler stub** (`POST /login`): pack the requested scopes into the base64 `code` payload as an
   extra part, e.g. `&sc={{form.scopes}}` alongside the existing `c=`/`s=`/`e=`/`n=`/`g=`/`f=` parts.
2. **Token stub** (`POST /token`): unpack `parts.sc`, and issue the `access_token` as a JWT via the
   `{{#jwt}}` helper (same RS256/JWKS as the `id_token`): claims `iss={{request.requestLine.baseUrl}}`,
   `aud={{parts.c}}` (client id), `sub={{parts.s}}`, `email={{parts.e}}`, `scope={{parts.sc}}`
   (space-separated, e.g. `openid email read write`), standard `iat`/`exp`.
   The `id_token` behaviour is left untouched.

**Result:** the scriptable demo login (`POST /login` with `clientId`, `email`, `scopes`, `state`,
`redirectUri` → 302 `code` → `POST /token`) yields a JWT the gateway validates locally against
`{auth-base}/oauth2/v3/certs`, with `SCOPE_read`/`SCOPE_write` authorities derived from the `scope` claim.
Requesting scopes without `write` produces a read-only token — the 403 demo.

No other template stubs (login page, `/device/code`, `/revoke`, `/v1/userinfo`, discovery) are modified.

---

## 6. Webhook receiver

**Mock API:** `Train Travel Webhook Receiver` · hostname `wm-train-travel-webhook` · type **unstructured**.

**One stub only:**

```
request: { "method": "POST" }        ← NO URL matcher at all (open URL match — accepts any POST path)
response: { "status": 200, "body": "{ \"received\": true }", "Content-Type": "application/json" }
```

The gateway POSTs the `newBooking` payload (booking + `links.self`) to the configured URL — any path works,
and the mock's **request journal** records the delivery, which is the demo evidence that the webhook fired.