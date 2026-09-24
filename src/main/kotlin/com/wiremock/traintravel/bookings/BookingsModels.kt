package com.wiremock.traintravel.bookings

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.wiremock.traintravel.common.LinksDto
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.UUID

// --- Downstream "Rail Reservations" models ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class Reservation(
    val reservationId: String,
    val status: String,
    val expiresAt: OffsetDateTime? = null,
    val createdAt: OffsetDateTime? = null,
    val tripRef: String,
    val passengerName: String,
    val bicycle: Boolean = false,
    val dog: Boolean = false,
    val holderId: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CreateReservationRequest(
    val tripRef: String,
    val passengerName: String,
    val bicycle: Boolean = false,
    val dog: Boolean = false,
    val holderId: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ReservationListResponse(val reservations: List<Reservation> = emptyList())

/** True when the hold has lapsed. CONFIRMED (paid) reservations never expire. */
fun Reservation.isExpired(now: OffsetDateTime = OffsetDateTime.now()): Boolean =
    status == "HELD" && expiresAt != null && expiresAt.isBefore(now)

// --- Public API models ---

data class CreateBookingRequest(
    @field:NotBlank(message = "must not be blank")
    @field:Size(max = 200, message = "must be at most 200 characters")
    val passengerName: String,
    val tripId: UUID,
    val hasBicycle: Boolean? = false,
    val hasDog: Boolean? = false,
)

data class BookingDto(
    val id: String,
    val tripId: String,
    val passengerName: String,
    val hasBicycle: Boolean,
    val hasDog: Boolean,
    val links: LinksDto,
) {
    companion object {
        fun from(reservation: Reservation, publicBaseUrl: String): BookingDto = BookingDto(
            id = reservation.reservationId,
            tripId = reservation.tripRef,
            passengerName = reservation.passengerName,
            hasBicycle = reservation.bicycle,
            hasDog = reservation.dog,
            links = LinksDto(self = "$publicBaseUrl/bookings/${reservation.reservationId}"),
        )
    }
}