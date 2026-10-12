package com.wiremock.traintravel.bookings

import com.wiremock.traintravel.common.NotFoundException
import com.wiremock.traintravel.config.AppProperties
import com.wiremock.traintravel.webhook.WebhookNotifier
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class BookingsServiceTest {

    @MockK
    lateinit var client: BookingsClient

    @MockK
    lateinit var notifier: WebhookNotifier

    private val props = AppProperties(
        publicBaseUrl = "http://localhost:8080",
        stations = AppProperties.Downstream("https://stations.example"),
        trips = AppProperties.Downstream("https://trips.example"),
        bookings = AppProperties.Downstream("https://bookings.example"),
        payments = AppProperties.Downstream("https://payments.example"),
        stripe = AppProperties.Stripe("https://stripe.example", "sk_test_123", "http://localhost:3000"),
        webhook = AppProperties.Webhook("https://webhook.example/new-booking", java.time.Duration.ofSeconds(3)),
        downstream = AppProperties.Timeouts(java.time.Duration.ofSeconds(5), java.time.Duration.ofSeconds(10)),
    )

    private val service by lazy { BookingsService(client, notifier, props) }

    private fun reservation(
        status: String = "HELD",
        expiresAt: OffsetDateTime = OffsetDateTime.now().plusHours(1),
    ) = Reservation(
        reservationId = "d956f2e6-93d0-446a-9ecf-44d8b3867501",
        status = status,
        expiresAt = expiresAt,
        createdAt = OffsetDateTime.now().minusMinutes(5),
        tripRef = "ea399ba1-6d95-433f-92d1-83f67b775594",
        passengerName = "Jane Smith",
        bicycle = true,
        dog = false,
        holderId = "user-a",
    )

    @Test
    fun `an expired HELD booking is not visible - 404`() {
        every { client.get(any(), any()) } returns reservation(expiresAt = OffsetDateTime.now().minusMinutes(1))

        assertThrows<NotFoundException> { service.get("user-a", "d956f2e6-93d0-446a-9ecf-44d8b3867501") }
    }

    @Test
    fun `a CONFIRMED booking survives its original expiry`() {
        every { client.get(any(), any()) } returns reservation(
            status = "CONFIRMED",
            expiresAt = OffsetDateTime.now().minusMinutes(1),
        )

        val booking = service.get("user-a", "d956f2e6-93d0-446a-9ecf-44d8b3867501")
        assertEquals("CONFIRMED", client.get("user-a", "d956f2e6-93d0-446a-9ecf-44d8b3867501")!!.status)
        assertEquals("http://localhost:8080/bookings/d956f2e6-93d0-446a-9ecf-44d8b3867501", booking.links.self)
    }

    @Test
    fun `unknown booking ids are 404`() {
        every { client.get(any(), any()) } returns null

        assertThrows<NotFoundException> { service.get("user-a", UUID.randomUUID().toString()) }
    }

    @Test
    fun `expired holds are filtered out of listings`() {
        every { client.list(any()) } returns listOf(
            reservation(expiresAt = OffsetDateTime.now().minusMinutes(1)),
            reservation(),
        )

        val result = service.list("user-a", 1, 10)

        assertEquals(1, result.data.size)
    }

    @Test
    fun `create stores the public trip id opaquely and fires the webhook`() {
        every { client.create(any(), any()) } returns reservation()
        every { notifier.newBooking(any()) } returns Unit

        val booking = service.create(
            "user-a",
            CreateBookingRequest(
                passengerName = "Jane Smith",
                tripId = UUID.fromString("ea399ba1-6d95-433f-92d1-83f67b775594"),
                hasBicycle = true,
                hasDog = false,
            ),
        )

        verify {
            client.create(
                match { it.tripRef == "ea399ba1-6d95-433f-92d1-83f67b775594" && it.holderId == "user-a" },
                "user-a",
            )
        }
        verify { notifier.newBooking(booking) }
        assertTrue(booking.links.self!!.endsWith("/bookings/d956f2e6-93d0-446a-9ecf-44d8b3867501"))
    }
}