package com.wiremock.traintravel.bookings

import com.wiremock.traintravel.common.CollectionDto
import com.wiremock.traintravel.common.currentUserId
import com.wiremock.traintravel.common.requireUuid
import com.wiremock.traintravel.common.validatePagination
import jakarta.validation.Valid
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
class BookingsController(private val service: BookingsService) {

    @GetMapping("/bookings")
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") limit: Int,
        auth: JwtAuthenticationToken,
    ): ResponseEntity<CollectionDto<BookingDto>> {
        validatePagination(page, limit)
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(service.list(currentUserId(auth), page, limit))
    }

    @PostMapping("/bookings")
    fun create(
        @Valid @RequestBody request: CreateBookingRequest,
        auth: JwtAuthenticationToken,
    ): ResponseEntity<BookingDto> {
        val booking = service.create(currentUserId(auth), request)
        return ResponseEntity.created(URI.create(booking.links.self!!))
            .cacheControl(CacheControl.noStore())
            .body(booking)
    }

    @GetMapping("/bookings/{bookingId}")
    fun get(@PathVariable bookingId: String, auth: JwtAuthenticationToken): ResponseEntity<BookingDto> {
        requireUuid(bookingId, "bookingId")
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(service.get(currentUserId(auth), bookingId))
    }

    @DeleteMapping("/bookings/{bookingId}")
    fun delete(@PathVariable bookingId: String, auth: JwtAuthenticationToken): ResponseEntity<Void> {
        requireUuid(bookingId, "bookingId")
        service.delete(currentUserId(auth), bookingId)
        return ResponseEntity.noContent()
            .cacheControl(CacheControl.noStore())
            .build()
    }
}