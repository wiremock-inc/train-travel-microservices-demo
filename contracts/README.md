# Contracts

The API contracts this gateway depends on — the *only* inputs needed to stand up the downstream
mock estate from scratch. Each contract describes one of the invented microservices behind the
[Train Travel API](../train-travel-openapi.yaml):

| File | Service | WireMock Cloud mock API |
|---|---|---|
| [`stations-openapi.json`](stations-openapi.json) | European Rail Reference Data | `wm-train-travel-stations` (REST) |
| [`trips-openapi.json`](trips-openapi.json) | European Rail Timetable | `wm-train-travel-trips` (REST) |
| [`bookings-openapi.json`](bookings-openapi.json) | Rail Reservations | `wm-train-travel-bookings` (REST, stateful) |
| [`payrail.wsdl`](payrail.wsdl) (+ [`payrail.xsd`](payrail.xsd)) | PayRail | `wm-train-travel-payments` (SOAP 1.1) |

The gateway builds directly against these too: `./gradlew generateSoapClient` generates the SOAP
client's JAXB classes from `payrail.xsd`, and `payrail.wsdl` is the contract the Spring WS client
implements.

The point of keeping them apart from [`../stubs/`](../stubs/): the contracts alone should be
enough to **generate a fresh set of mock APIs in WireMock Cloud** (push each OpenAPI description
as a mock API, generate stubs from it, then drive the gateway and its E2E against them) — without
any of the hand-authored stub mappings. The stubs that the currently deployed mocks were built
with live next door, so the two can evolve separately.

Not covered by a contract here: the mocked Google IdP (a pre-built WireMock Cloud
template) and the webhook receiver (a plain POST sink, no API to describe — see
[`../stubs/webhook-stubs.json`](../stubs/webhook-stubs.json)).