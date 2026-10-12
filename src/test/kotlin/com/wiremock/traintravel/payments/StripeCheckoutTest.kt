package com.wiremock.traintravel.payments

import com.wiremock.traintravel.bookings.BookingsService
import com.wiremock.traintravel.bookings.Reservation
import com.wiremock.traintravel.common.BadRequestException
import com.wiremock.traintravel.common.NotFoundException
import com.wiremock.traintravel.config.AppProperties
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.math.BigDecimal
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class StripeCheckoutTest {

    @MockK
    lateinit var bookingsService: BookingsService

    @MockK
    lateinit var stripeClient: StripeClient

    private val props = AppProperties(
        publicBaseUrl = "http://localhost:8080",
        stations = AppProperties.Downstream("https://stations.example"),
        trips = AppProperties.Downstream("https://trips.example"),
        bookings = AppProperties.Downstream("https://bookings.example"),
        payments = AppProperties.Downstream("https://payments.example"),
        stripe = AppProperties.Stripe("https://stripe.example", "sk_test_123", "http://localhost:3000"),
        webhook = AppProperties.Webhook("https://webhook.example/new-booking", Duration.ofSeconds(3)),
        downstream = AppProperties.Timeouts(Duration.ofSeconds(5), Duration.ofSeconds(10)),
    )

    private val bookingId = "1725ff48-ab45-4bb5-9d02-88745177dedb"
    private val sessionId = "cs_test_a1b2c3"

    private val checkout by lazy { StripeCheckout(bookingsService, stripeClient, props) }

    private fun reservation(status: String = "HELD") = Reservation(
        reservationId = bookingId,
        status = status,
        expiresAt = OffsetDateTime.now().plusHours(1),
        createdAt = OffsetDateTime.now().minusMinutes(5),
        tripRef = "ea399ba1-6d95-433f-92d1-83f67b775594",
        passengerName = "Jane Smith",
        bicycle = true,
        dog = false,
        holderId = "user-a",
    )

    private fun session(
        status: String = "complete",
        paymentStatus: String = "paid",
        clientReferenceId: String? = bookingId,
    ) = StripeCheckoutSession(
        id = sessionId,
        url = "https://stripe.example/hosted/checkout/$sessionId",
        status = status,
        paymentStatus = paymentStatus,
        clientReferenceId = clientReferenceId,
        amountTotal = 8950,
        currency = "eur",
    )

    @Test
    fun `starting a checkout creates a session in minor units that returns to the booking's payment page`() {
        every { bookingsService.getReservation("user-a", bookingId) } returns reservation()
        every { stripeClient.createCheckoutSession(any(), any(), any(), any(), any(), any()) } returns
            session(status = "open", paymentStatus = "unpaid")

        val started = checkout.start("user-a", bookingId, CheckoutRequest(BigDecimal("89.50"), "EUR"))

        assertEquals(sessionId, started.id)
        assertEquals("https://stripe.example/hosted/checkout/$sessionId", started.url)
        verify {
            stripeClient.createCheckoutSession(
                bookingId = bookingId,
                description = "Train ticket for Jane Smith",
                amountMinor = 8950,
                currency = "eur",
                successUrl = "http://localhost:3000/payment/$bookingId?stripe_session_id={CHECKOUT_SESSION_ID}",
                cancelUrl = "http://localhost:3000/payment/$bookingId?stripe_cancelled=true",
            )
        }
    }

    @Test
    fun `starting a checkout for an already-confirmed booking is a 400 before any Stripe call`() {
        every { bookingsService.getReservation("user-a", bookingId) } returns reservation(status = "CONFIRMED")

        val ex = assertThrows<BadRequestException> {
            checkout.start("user-a", bookingId, CheckoutRequest(BigDecimal("89.50"), "eur"))
        }
        assertEquals("Booking has already been paid.", ex.message)
        verify(exactly = 0) { stripeClient.createCheckoutSession(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `starting a checkout with an invalid currency is rejected`() {
        val ex = assertThrows<BadRequestException> {
            checkout.start("user-a", bookingId, CheckoutRequest(BigDecimal("89.50"), "usd"))
        }
        assertTrue(ex.message!!.contains("currency"))
    }

    @Test
    fun `completing a paid session confirms the booking and reports the amount in major units`() {
        every { bookingsService.getReservation("user-a", bookingId) } returns reservation()
        every { bookingsService.confirm("user-a", bookingId) } returns Unit
        every { stripeClient.getCheckoutSession(sessionId) } returns session()

        val payment = checkout.complete("user-a", bookingId, sessionId)

        verify { bookingsService.confirm("user-a", bookingId) }
        assertEquals(sessionId, payment.id)
        assertEquals(BigDecimal("89.50"), payment.amount)
        assertEquals("eur", payment.currency)
        assertEquals("succeeded", payment.status)
        assertEquals("http://localhost:8080/bookings/$bookingId", payment.links.booking)
    }

    @Test
    fun `completing an unpaid session does not confirm the booking and surfaces as 400`() {
        every { bookingsService.getReservation("user-a", bookingId) } returns reservation()
        every { stripeClient.getCheckoutSession(sessionId) } returns session(status = "open", paymentStatus = "unpaid")

        val ex = assertThrows<BadRequestException> { checkout.complete("user-a", bookingId, sessionId) }
        assertEquals("Stripe Checkout session $sessionId has not been paid.", ex.message)
        verify(exactly = 0) { bookingsService.confirm(any(), any()) }
    }

    @Test
    fun `a session paid for a different booking cannot confirm this one`() {
        every { bookingsService.getReservation("user-a", bookingId) } returns reservation()
        every { stripeClient.getCheckoutSession(sessionId) } returns session(clientReferenceId = "another-booking")

        assertThrows<NotFoundException> { checkout.complete("user-a", bookingId, sessionId) }
        verify(exactly = 0) { bookingsService.confirm(any(), any()) }
    }

    @Test
    fun `an unknown session is a 404`() {
        every { bookingsService.getReservation("user-a", bookingId) } returns reservation()
        every { stripeClient.getCheckoutSession(sessionId) } returns null

        assertThrows<NotFoundException> { checkout.complete("user-a", bookingId, sessionId) }
    }

    @Test
    fun `completing again after the booking is confirmed is idempotent`() {
        every { bookingsService.getReservation("user-a", bookingId) } returns reservation(status = "CONFIRMED")
        every { stripeClient.getCheckoutSession(sessionId) } returns session()

        val payment = checkout.complete("user-a", bookingId, sessionId)

        assertEquals("succeeded", payment.status)
        verify(exactly = 0) { bookingsService.confirm(any(), any()) }
    }
}
