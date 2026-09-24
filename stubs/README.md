# Stub mappings

The hand-authored WireMock stub sets behind the currently deployed mock estate. These are what
actually produce the behaviour the gateway (and the E2E journey) depends on:

| File | What it does | Techniques |
|---|---|---|
| [`stations-stubs.json`](stations-stubs.json) | `GET /station-data` | `data-exists` CSV matching, optional country filter, empty-fallback stub |
| [`trips-stubs.json`](trips-stubs.json) | `GET /connections` | `data-exists` CSV matching, date-stamped response templating, empty-route fallback |
| [`bookings-stubs.json`](bookings-stubs.json) | reservations CRUD + confirm | KV state: `change-state` listeners, per-user contexts `reservations:{{request.headers.x-user-id}}`, `require-state`, `listState` |
| [`payments-stubs.json`](payments-stubs.json) | SOAP `ProcessPayment` | body-matched stubs (prefix-agnostic), masked-number `regexExtract`, decline/approve/fault drivers |
| [`webhook-stubs.json`](webhook-stubs.json) | the `newBooking` sink | open-URL POST match; the request journal is the webhook evidence |

Each file is the WireMock multi-stub import format (`{"mappings": [...]}`): push it via the
WireMock Cloud MCP `push` / `import` tools, or paste it into the UI. Note the platform quirks
baked into these definitions: CSV column names are lowercased, `regexExtract` takes no capture
group, and a `data-exists` query with zero rows does not match (hence the low-priority
empty-response fallbacks).

The API contracts these stubs implement live in [`../contracts/`](../contracts/). The intended
demo arc: from the contracts alone, generate the mock APIs and their stubs in WireMock Cloud —
then, if you want the curated behaviour (realistic data, statefulness, decline drivers), layer
these on top.