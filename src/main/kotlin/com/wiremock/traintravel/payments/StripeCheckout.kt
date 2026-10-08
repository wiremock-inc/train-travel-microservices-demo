package com.wiremock.traintravel.payments

import com.wiremock.traintravel.bookings.BookingsService
import com.wiremock.traintravel.common.BadRequestException
import com.wiremock.traintravel.common.LinksDto
import com.wiremock.traintravel.common.NotFoundException
import com.wiremock.traintravel.config.AppProperties
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Payment via Stripe's hosted Checkout page: create a session -> the browser pays on Stripe's
 * page and is sent back with the session id -> verify the session is paid, then confirm the
 * reservation. An unpaid or cancelled session leaves the booking HELD.
 */
@Service
class StripeCheckout(
    private val bookingsService: BookingsService,
    private val stripeClient: StripeClient,
    private val props: AppProperties,
) {

    fun start(userId: String, bookingId: String, request: CheckoutRequest): CheckoutSessionDto {
        val currency = request.currency.lowercase()
        requireSupportedCurrency(currency)

        val reservation = bookingsService.getReservation(userId, bookingId)
        if (reservation.status == "CONFIRMED") {
            throw BadRequestException("Booking has already been paid.")
        }

        val paymentPage = "${props.stripe.returnBaseUrl}/payment/$bookingId"
        val session = stripeClient.createCheckoutSession(
            bookingId = bookingId,
            description = "Train ticket for ${reservation.passengerName}",
            // Every supported currency has two decimal places
            amountMinor = request.amount.movePointRight(2).longValueExact(),
            currency = currency,
            // Stripe substitutes the {CHECKOUT_SESSION_ID} placeholder itself
            successUrl = "$paymentPage?stripe_session_id={CHECKOUT_SESSION_ID}",
            cancelUrl = "$paymentPage?stripe_cancelled=true",
        )
        return CheckoutSessionDto(
            id = session.id,
            url = session.url ?: throw IllegalStateException("Stripe returned a Checkout session with no URL"),
        )
    }

    fun complete(userId: String, bookingId: String, sessionId: String): CheckoutPaymentDto {
        val reservation = bookingsService.getReservation(userId, bookingId)

        val session = stripeClient.getCheckoutSession(sessionId)
            ?.takeIf { it.clientReferenceId == bookingId }
            ?: throw NotFoundException("Stripe Checkout session $sessionId not found for booking $bookingId")
        if (session.paymentStatus != "paid") {
            throw BadRequestException("Stripe Checkout session $sessionId has not been paid.")
        }

        // The browser may land on the return URL more than once (refresh, back button)
        if (reservation.status != "CONFIRMED") {
            bookingsService.confirm(userId, bookingId)
        }
        return CheckoutPaymentDto(
            id = session.id,
            amount = BigDecimal.valueOf(session.amountTotal ?: 0, 2),
            currency = session.currency ?: "",
            status = "succeeded",
            links = LinksDto(booking = "${props.publicBaseUrl}/bookings/$bookingId"),
        )
    }
}
