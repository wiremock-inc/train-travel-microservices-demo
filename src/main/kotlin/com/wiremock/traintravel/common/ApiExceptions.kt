package com.wiremock.traintravel.common

/** Request-level failure the gateway turns into a public 400. */
class BadRequestException(message: String) : RuntimeException(message)

/** Resource-level failure the gateway turns into a public 404. */
class NotFoundException(message: String) : RuntimeException(message)

/** A downstream service failed or timed out - mapped to a public 500. */
class DownstreamException(message: String) : RuntimeException(message)

/** PayRail declined the payment - booking stays HELD, mapped to a public 400. */
class PaymentDeclinedException(val reason: String) :
    RuntimeException("Payment was declined by the payment provider ($reason)")

/** PayRail returned a validation-class SOAP fault - mapped to a public 400. */
class PayRailValidationException(message: String) : RuntimeException(message)

/** A booking id was not found in the (per-user) reservations store. */
class BookingNotFoundException(val bookingId: String) : RuntimeException("Booking $bookingId not found")