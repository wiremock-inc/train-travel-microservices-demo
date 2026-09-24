package com.wiremock.traintravel.payments

import com.wiremock.traintravel.common.currentUserId
import com.wiremock.traintravel.common.requireUuid
import jakarta.validation.Valid
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class PaymentsController(private val saga: PaymentSaga) {

    @PostMapping("/bookings/{bookingId}/payment")
    fun pay(
        @PathVariable bookingId: String,
        @Valid @RequestBody request: PaymentRequest,
        auth: JwtAuthenticationToken,
    ): ResponseEntity<PaymentResponseDto> {
        requireUuid(bookingId, "bookingId")
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(saga.pay(currentUserId(auth), bookingId, request))
    }
}