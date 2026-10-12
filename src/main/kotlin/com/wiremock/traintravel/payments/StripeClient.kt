package com.wiremock.traintravel.payments

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.wiremock.traintravel.config.AppProperties
import com.wiremock.traintravel.config.RestClientFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

/** The parts of a Stripe Checkout Session the gateway cares about. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class StripeCheckoutSession(
    val id: String,
    val url: String? = null,
    val status: String? = null,
    val paymentStatus: String? = null,
    val clientReferenceId: String? = null,
    val amountTotal: Long? = null,
    val currency: String? = null,
)

/**
 * Client for the Stripe API (Checkout Sessions only). Stripe takes form-encoded requests
 * and returns snake_case JSON.
 */
@Component
class StripeClient(props: AppProperties, factory: RestClientFactory) {

    private val rest: RestClient = factory.create(
        props.stripe.baseUrl,
        mapper = ObjectMapper()
            .findAndRegisterModules()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE),
    ).mutate()
        .defaultHeaders { it.setBearerAuth(props.stripe.secretKey) }
        .build()

    fun createCheckoutSession(
        bookingId: String,
        description: String,
        amountMinor: Long,
        currency: String,
        successUrl: String,
        cancelUrl: String,
    ): StripeCheckoutSession {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("mode", "payment")
            add("client_reference_id", bookingId)
            add("success_url", successUrl)
            add("cancel_url", cancelUrl)
            add("line_items[0][quantity]", "1")
            add("line_items[0][price_data][currency]", currency)
            add("line_items[0][price_data][unit_amount]", amountMinor.toString())
            add("line_items[0][price_data][product_data][name]", description)
        }
        return rest.post()
            .uri("/v1/checkout/sessions")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(StripeCheckoutSession::class.java)
            ?: throw IllegalStateException("Stripe returned an empty body on Checkout session create")
    }

    /** Stripe's 404 for an unknown session id is a *value*, not an error. */
    fun getCheckoutSession(sessionId: String): StripeCheckoutSession? =
        try {
            rest.get()
                .uri("/v1/checkout/sessions/{id}", sessionId)
                .retrieve()
                .onStatus({ it.value() == 404 }) { _, _ -> throw SessionNotFound() }
                .body(StripeCheckoutSession::class.java)
        } catch (e: SessionNotFound) {
            null
        }

    private class SessionNotFound : RuntimeException()
}
