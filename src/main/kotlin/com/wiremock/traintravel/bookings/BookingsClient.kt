package com.wiremock.traintravel.bookings

import com.wiremock.traintravel.common.BookingNotFoundException
import com.wiremock.traintravel.config.AppProperties
import com.wiremock.traintravel.config.RestClientFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Client for the Bookings Service (Rail Reservations).
 * Every call carries X-User-Id; the service scopes reservations to that header,
 * so one user's reservations are invisible to another.
 */
@Component
class BookingsClient(props: AppProperties, factory: RestClientFactory) {

    private val rest: RestClient = factory.create(props.bookings.baseUrl)

    fun create(request: CreateReservationRequest, userId: String): Reservation =
        rest.post()
            .uri("/reservations")
            .header("X-User-Id", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(Reservation::class.java)
            ?: throw IllegalStateException("Bookings service returned an empty body on create")

    fun list(userId: String): List<Reservation> =
        rest.get()
            .uri("/reservations")
            .header("X-User-Id", userId)
            .retrieve()
            .body(ReservationListResponse::class.java)
            ?.reservations
            ?: emptyList()

    /** Downstream 404 (unknown id, or another user's) is a *value*, not an error. */
    fun get(reservationId: String, userId: String): Reservation? =
        try {
            rest.get()
                .uri("/reservations/{id}", reservationId)
                .header("X-User-Id", userId)
                .retrieve()
                .onStatus({ it.value() == 404 }) { _, _ -> throw BookingNotFoundException(reservationId) }
                .body(Reservation::class.java)
        } catch (e: BookingNotFoundException) {
            null
        }

    fun confirm(reservationId: String, userId: String): Reservation =
        rest.post()
            .uri("/reservations/{id}/confirm", reservationId)
            .header("X-User-Id", userId)
            .retrieve()
            .onStatus({ it.value() == 404 }) { _, _ -> throw BookingNotFoundException(reservationId) }
            .body(Reservation::class.java)
            ?: throw IllegalStateException("Bookings service returned an empty body on confirm")

    fun delete(reservationId: String, userId: String) {
        rest.delete()
            .uri("/reservations/{id}", reservationId)
            .header("X-User-Id", userId)
            .retrieve()
            .onStatus({ it.value() == 404 }) { _, _ -> throw BookingNotFoundException(reservationId) }
            .toBodilessEntity()
    }
}