package com.wiremock.traintravel.webhook

import com.fasterxml.jackson.databind.ObjectMapper
import com.wiremock.traintravel.bookings.BookingDto
import com.wiremock.traintravel.config.AppProperties
import com.wiremock.traintravel.config.RestClientFactory
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Fires the spec's outbound newBooking webhook at the configured receiver whenever a
 * booking is created. Fire-and-forget: a failed delivery is logged, never propagated -
 * booking creation must not fail because a subscriber is down.
 *
 * Note the webhook payload is a *public* snake_case BookingDto, so this client uses the
 * global (Spring-managed) ObjectMapper rather than the downstream camelCase one.
 */
@Component
class WebhookNotifier(
    private val props: AppProperties,
    factory: RestClientFactory,
    globalMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(WebhookNotifier::class.java)

    private val rest: RestClient = factory.create(
        "",
        readTimeout = props.webhook.timeout,
        mapper = globalMapper,
    )

    fun newBooking(booking: BookingDto) {
        try {
            rest.post()
                .uri(props.webhook.url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(booking)
                .retrieve()
                .toBodilessEntity()
        } catch (e: Exception) {
            log.warn("Failed to deliver newBooking webhook for booking {}: {}", booking.id, e.message)
        }
    }
}