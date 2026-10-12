package com.wiremock.traintravel.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("traintravel")
data class AppProperties(
    val publicBaseUrl: String,
    val stations: Downstream,
    val trips: Downstream,
    val bookings: Downstream,
    val payments: Downstream,
    val stripe: Stripe,
    val webhook: Webhook,
    val downstream: Timeouts,
) {
    data class Downstream(val baseUrl: String)
    data class Stripe(val baseUrl: String, val secretKey: String, val returnBaseUrl: String)
    data class Webhook(val url: String, val timeout: Duration)
    data class Timeouts(val connectTimeout: Duration, val readTimeout: Duration)
}