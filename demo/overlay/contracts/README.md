# Contracts

The API contracts this gateway depends on. Each describes one of the downstream services behind the
[Train Travel API](../train-travel-openapi.yaml):

| File | Service |
|---|---|
| [`stations-openapi.json`](stations-openapi.json) | European Rail Reference Data (REST) |
| [`trips-openapi.json`](trips-openapi.json) | European Rail Timetable (REST) |
| [`bookings-openapi.json`](bookings-openapi.json) | Rail Reservations (REST) |
| [`payrail.wsdl`](payrail.wsdl) (+ [`payrail.xsd`](payrail.xsd)) | PayRail (SOAP 1.1) |

The gateway builds directly against these too: `./gradlew generateSoapClient` generates the SOAP
client's JAXB classes from `payrail.xsd`, and `payrail.wsdl` is the contract the Spring WS client
implements.

Not covered by a contract here: the Google IdP and the webhook receiver (a plain POST sink).
