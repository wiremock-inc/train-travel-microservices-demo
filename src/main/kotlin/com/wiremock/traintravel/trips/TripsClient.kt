package com.wiremock.traintravel.trips

import com.wiremock.traintravel.config.AppProperties
import com.wiremock.traintravel.config.RestClientFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.LocalDate

/** Client for the Trips Service (mocked in WireMock Cloud, CSV data-driven with date-stamped templating). */
@Component
class TripsClient(props: AppProperties, factory: RestClientFactory) {

    private val rest: RestClient = factory.create(props.trips.baseUrl)

    fun findConnections(from: String, to: String, date: LocalDate, page: Int, pageSize: Int): ConnectionsResponse =
        rest.get()
            .uri { builder ->
                builder.path("/connections")
                    .queryParam("from", from)
                    .queryParam("to", to)
                    .queryParam("date", date.toString())
                    .queryParam("page", page)
                    .queryParam("pageSize", pageSize)
                    .build()
            }
            .retrieve()
            .body(ConnectionsResponse::class.java)
            ?: ConnectionsResponse()
}