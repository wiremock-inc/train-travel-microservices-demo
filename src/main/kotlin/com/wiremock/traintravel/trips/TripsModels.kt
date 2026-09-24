package com.wiremock.traintravel.trips

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.wiremock.traintravel.common.Uuids
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

// --- Downstream "European Rail Timetable" models ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class ConnectionsResponse(
    val connections: List<Connection> = emptyList(),
    val page: Int = 1,
    val pageSize: Int = 10,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Connection(
    val connectionId: String,
    val from: StationPoint,
    val to: StationPoint,
    val operator: Operator,
    val fare: Fare,
    val facilities: Facilities,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class StationPoint(val stationCode: String, val scheduledDeparture: String? = null, val scheduledArrival: String? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Operator(val code: String? = null, val name: String? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Fare(val amount: BigDecimal, val currency: String? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Facilities(val bicyclesAllowed: Boolean = false, val dogsAllowed: Boolean = false)
}

// --- Public API models ---

data class TripDto(
    val id: UUID,
    val origin: UUID,
    val destination: UUID,
    val departureTime: OffsetDateTime,
    val arrivalTime: OffsetDateTime,
    val operator: String,
    val price: BigDecimal,
    val bicyclesAllowed: Boolean,
    val dogsAllowed: Boolean,
    val links: TripLinksDto,
) {
    companion object {
        fun from(connection: Connection, publicBaseUrl: String): TripDto {
            val departure = OffsetDateTime.parse(connection.from.scheduledDeparture)
            val originUuid = Uuids.stationUuid(connection.from.stationCode)
            val destinationUuid = Uuids.stationUuid(connection.to.stationCode)
            return TripDto(
                id = Uuids.tripUuid(connection.from.stationCode, connection.to.stationCode, connection.from.scheduledDeparture!!),
                origin = originUuid,
                destination = destinationUuid,
                departureTime = departure,
                arrivalTime = OffsetDateTime.parse(connection.to.scheduledArrival),
                operator = connection.operator.name ?: "",
                price = connection.fare.amount,
                bicyclesAllowed = connection.facilities.bicyclesAllowed,
                dogsAllowed = connection.facilities.dogsAllowed,
                links = TripLinksDto(
                    self = "$publicBaseUrl/trips/${Uuids.tripUuid(connection.from.stationCode, connection.to.stationCode, connection.from.scheduledDeparture!!)}",
                    origin = "$publicBaseUrl/stations/$originUuid",
                    destination = "$publicBaseUrl/stations/$destinationUuid",
                ),
            )
        }
    }
}

/** Per-trip links; the spec documents self/origin/destination (no GET /trips/{id} exists - self is notional). */
data class TripLinksDto(
    val self: String,
    val origin: String,
    val destination: String,
)