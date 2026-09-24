package com.wiremock.traintravel.bookings

import com.wiremock.traintravel.common.BadRequestException
import com.wiremock.traintravel.common.CollectionDto
import com.wiremock.traintravel.common.NotFoundException
import com.wiremock.traintravel.common.paginationLinks
import com.wiremock.traintravel.config.AppProperties
import com.wiremock.traintravel.webhook.WebhookNotifier
import org.springframework.stereotype.Service

@Service
class BookingsService(
    private val client: BookingsClient,
    private val notifier: WebhookNotifier,
    private val props: AppProperties,
) {

    /** Creates the hold and fires the newBooking webhook (fire-and-forget). */
    fun create(userId: String, request: CreateBookingRequest): BookingDto {
        val reservation = client.create(
            CreateReservationRequest(
                tripRef = request.tripId.toString(),
                passengerName = request.passengerName,
                bicycle = request.hasBicycle ?: false,
                dog = request.hasDog ?: false,
                holderId = userId,
            ),
            userId,
        )
        val booking = BookingDto.from(reservation, props.publicBaseUrl)
        notifier.newBooking(booking)
        return booking
    }

    /** The authenticated user's visible bookings: un-expired only, creation order, gateway-side paging. */
    fun list(userId: String, page: Int, limit: Int): CollectionDto<BookingDto> {
        val visible = client.list(userId).filter { !it.isExpired() }
        val pageItems = visible.drop((page - 1) * limit).take(limit)
        val links = paginationLinks(props.publicBaseUrl, "/bookings", emptyMap(), page, limit, visible.size)
        return CollectionDto(pageItems.map { BookingDto.from(it, props.publicBaseUrl) }, links)
    }

    fun get(userId: String, bookingId: String): BookingDto =
        BookingDto.from(getVisibleReservation(userId, bookingId), props.publicBaseUrl)

    fun delete(userId: String, bookingId: String) {
        getVisibleReservation(userId, bookingId)
        client.delete(bookingId, userId)
    }

    fun confirm(userId: String, bookingId: String) {
        client.confirm(bookingId, userId)
    }

    /** Raw reservation for the payment saga. */
    fun getReservation(userId: String, bookingId: String): Reservation = getVisibleReservation(userId, bookingId)

    /**
     * The single expiry rule of the system: a HELD reservation past its expiresAt behaves as if
     * it never existed (404), for every operation. CONFIRMED (paid) reservations never expire -
     * that is the observable difference payment makes.
     */
    private fun getVisibleReservation(userId: String, bookingId: String): Reservation {
        val reservation = client.get(bookingId, userId)
            ?: throw NotFoundException("Booking $bookingId not found")
        if (reservation.isExpired()) {
            throw NotFoundException("Booking $bookingId not found")
        }
        if (reservation.status != "HELD" && reservation.status != "CONFIRMED") {
            throw BadRequestException("Booking $bookingId is in an unknown state: ${reservation.status}")
        }
        return reservation
    }
}