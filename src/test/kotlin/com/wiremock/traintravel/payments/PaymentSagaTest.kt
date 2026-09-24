package com.wiremock.traintravel.payments

import com.wiremock.traintravel.bookings.BookingsService
import com.wiremock.traintravel.bookings.Reservation
import com.wiremock.traintravel.common.BadRequestException
import com.wiremock.traintravel.common.PaymentDeclinedException
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
class PaymentSagaTest {

    @MockK
    lateinit var bookingsService: BookingsService

    @MockK
    lateinit var payRailClient: PayRailClient

    private val props = AppProperties(
        publicBaseUrl = "http://localhost:8080",
        stations = AppProperties.Downstream("https://stations.example"),
        trips = AppProperties.Downstream("https://trips.example"),
        bookings = AppProperties.Downstream("https://bookings.example"),
        payments = AppProperties.Downstream("https://payments.example"),
        webhook = AppProperties.Webhook("https://webhook.example/new-booking", Duration.ofSeconds(3)),
        downstream = AppProperties.Timeouts(Duration.ofSeconds(5), Duration.ofSeconds(10)),
    )

    private val bookingId = "1725ff48-ab45-4bb5-9d02-88745177dedb"

    private val saga by lazy { PaymentSaga(bookingsService, payRailClient, props) }

    private fun heldReservation(status: String = "HELD") = Reservation(
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

    private val cardRequest = PaymentRequest(
        amount = BigDecimal("89.50"),
        currency = "EUR",
        source = CardSourceDto(
            name = "J. Doe",
            number = "4242424242424242",
            cvc = "123",
            expMonth = 12,
            expYear = 2030,
            addressCountry = "gb",
        ),
    )

    @Test
    fun `an approved card payment confirms the booking and maps the response`() {
        every { bookingsService.getReservation("user-a", bookingId) } returns heldReservation()
        every { bookingsService.confirm("user-a", bookingId) } returns Unit
        every { payRailClient.processPayment(any(), any(), any(), any()) } returns PayRailApproval(
            transactionId = "2e3b4f5a-6b7c-8d9e-0f1a-2b3c4d5e6f7a",
            authorisedAmount = BigDecimal("89.50"),
            maskedCardNumber = "************4242",
            maskedAccountNumber = null,
            processedAt = "2026-09-24T12:34:56Z",
        )

        val response = saga.pay("user-a", bookingId, cardRequest)

        verify { bookingsService.confirm("user-a", bookingId) }
        assertEquals("succeeded", response.status)
        assertEquals("2e3b4f5a-6b7c-8d9e-0f1a-2b3c4d5e6f7a", response.id)
        assertEquals("eur", response.currency)
        assertEquals("************4242", (response.source as CardSourceResponse).number)
        assertEquals("card", (response.source as CardSourceResponse).objectName)
        assertEquals("http://localhost:8080/bookings/$bookingId", response.links.booking)
    }

    @Test
    fun `a declined payment does not confirm the booking and surfaces as 400`() {
        every { bookingsService.getReservation("user-a", bookingId) } returns heldReservation()
        every { payRailClient.processPayment(any(), any(), any(), any()) } throws
            PaymentDeclinedException("card_declined")

        assertThrows<PaymentDeclinedException> { saga.pay("user-a", bookingId, cardRequest) }
        verify(exactly = 0) { bookingsService.confirm(any(), any()) }
    }

    @Test
    fun `paying an already-confirmed booking is a 400 before any SOAP call`() {
        every { bookingsService.getReservation("user-a", bookingId) } returns heldReservation(status = "CONFIRMED")

        val ex = assertThrows<BadRequestException> { saga.pay("user-a", bookingId, cardRequest) }
        assertEquals("Booking has already been paid.", ex.message)
        verify(exactly = 0) { payRailClient.processPayment(any(), any(), any(), any()) }
    }

    @Test
    fun `an invalid currency is rejected`() {
        val ex = assertThrows<BadRequestException> {
            saga.pay("user-a", bookingId, cardRequest.copy(currency = "usd"))
        }
        assertTrue(ex.message!!.contains("currency"))
    }

    @Test
    fun `a malformed card number is rejected`() {
        val bad = cardRequest.copy(
            source = (cardRequest.source as CardSourceDto).copy(number = "4242"),
        )
        val ex = assertThrows<BadRequestException> { saga.pay("user-a", bookingId, bad) }
        assertTrue(ex.message!!.contains("source.number"))
    }

    @Test
    fun `a bank account payment maps the masked account number`() {
        every { bookingsService.getReservation("user-a", bookingId) } returns heldReservation()
        every { bookingsService.confirm("user-a", bookingId) } returns Unit
        every { payRailClient.processPayment(any(), any(), any(), any()) } returns PayRailApproval(
            transactionId = "2e3b4f5a-6b7c-8d9e-0f1a-2b3c4d5e6f7a",
            authorisedAmount = BigDecimal("100.50"),
            maskedCardNumber = null,
            maskedAccountNumber = "*********2345",
            processedAt = "2026-09-24T12:34:56Z",
        )

        val response = saga.pay(
            "user-a", bookingId,
            PaymentRequest(
                amount = BigDecimal("100.50"),
                currency = "gbp",
                source = BankAccountSourceDto(
                    name = "J. Doe",
                    number = "00012345",
                    sortCode = "000123",
                    accountType = "individual",
                    bankName = "Starling Bank",
                    country = "gb",
                ),
            ),
        )

        val source = response.source as BankAccountSourceResponse
        assertEquals("bank_account", source.objectName)
        assertEquals("*********2345", source.number)
    }
}