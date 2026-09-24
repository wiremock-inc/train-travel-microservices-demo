package com.wiremock.traintravel.trips

import com.wiremock.traintravel.common.BadRequestException
import com.wiremock.traintravel.common.Uuids
import com.wiremock.traintravel.config.AppProperties
import com.wiremock.traintravel.stations.StationsService
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class TripsServiceTest {

    @MockK
    lateinit var client: TripsClient

    @MockK
    lateinit var stationsService: StationsService

    private val props = AppProperties(
        publicBaseUrl = "http://localhost:8080",
        stations = AppProperties.Downstream("https://stations.example"),
        trips = AppProperties.Downstream("https://trips.example"),
        bookings = AppProperties.Downstream("https://bookings.example"),
        payments = AppProperties.Downstream("https://payments.example"),
        webhook = AppProperties.Webhook("https://webhook.example/new-booking", Duration.ofSeconds(3)),
        downstream = AppProperties.Timeouts(Duration.ofSeconds(5), Duration.ofSeconds(10)),
    )

    private val service by lazy { TripsService(client, stationsService, props) }

    private fun connection(
        connectionId: String = "ICE-1001",
        bicycles: Boolean = true,
        dogs: Boolean = false,
    ) = Connection(
        connectionId = connectionId,
        from = Connection.StationPoint("DE-BER-HBF", scheduledDeparture = "2026-10-01T06:00:00Z"),
        to = Connection.StationPoint("DE-MUC-HBF", scheduledArrival = "2026-10-01T10:00:00Z"),
        operator = Connection.Operator("DB", "Deutsche Bahn"),
        fare = Connection.Fare(BigDecimal("89.90"), "EUR"),
        facilities = Connection.Facilities(bicyclesAllowed = bicycles, dogsAllowed = dogs),
    )

    @Test
    fun `trips map to the public shape with deterministic uuids`() {
        every { stationsService.stationCodeFor(any()) } returns "DE-BER-HBF" andThen "DE-MUC-HBF"
        every { client.findConnections("DE-BER-HBF", "DE-MUC-HBF", LocalDate.parse("2026-10-01"), 1, 10) } returns
            ConnectionsResponse(listOf(connection()), 1, 10)

        val result = service.findTrips(
            UUID.randomUUID(), UUID.randomUUID(), LocalDate.parse("2026-10-01"),
            bicycles = false, dogs = false, page = 1, limit = 10,
        )

        val trip = result.data.single()
        assertEquals(Uuids.stationUuid("DE-BER-HBF"), trip.origin)
        assertEquals(Uuids.stationUuid("DE-MUC-HBF"), trip.destination)
        assertEquals("Deutsche Bahn", trip.operator)
        assertEquals(0, BigDecimal("89.90").compareTo(trip.price))
        assertEquals(
            Uuids.tripUuid("DE-BER-HBF", "DE-MUC-HBF", "2026-10-01T06:00:00Z"),
            trip.id,
        )
        assertEquals("http://localhost:8080/stations/${trip.origin}", trip.links.origin)
    }

    @Test
    fun `bicycle and dog filters are applied`() {
        every { stationsService.stationCodeFor(any()) } returns "DE-BER-HBF" andThen "DE-MUC-HBF"
        every { client.findConnections(any(), any(), any(), any(), any()) } returns ConnectionsResponse(
            listOf(
                connection("ICE-1001", bicycles = true, dogs = true),
                connection("ICE-1002", bicycles = true, dogs = false),
                connection("ICE-1003", bicycles = false, dogs = true),
            ),
            1, 10,
        )

        val result = service.findTrips(
            UUID.randomUUID(), UUID.randomUUID(), LocalDate.parse("2026-10-01"),
            bicycles = true, dogs = true, page = 1, limit = 10,
        )

        assertEquals(1, result.data.size)
        assertEquals("2026-10-01T06:00Z", result.data.single().departureTime.toString())
    }

    @Test
    fun `unknown origin station is a 400`() {
        every { stationsService.stationCodeFor(any()) } returns null

        assertThrows<BadRequestException> {
            service.findTrips(
                UUID.randomUUID(), UUID.randomUUID(), LocalDate.parse("2026-10-01"),
                bicycles = false, dogs = false, page = 1, limit = 10,
            )
        }
    }
}