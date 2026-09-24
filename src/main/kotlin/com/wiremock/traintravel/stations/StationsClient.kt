package com.wiremock.traintravel.stations

import com.wiremock.traintravel.config.AppProperties
import com.wiremock.traintravel.config.RestClientFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/** Client for the Stations Service (mocked in WireMock Cloud, CSV data-driven). */
@Component
class StationsClient(props: AppProperties, factory: RestClientFactory) {

    private val rest: RestClient = factory.create(props.stations.baseUrl)

    /** Fetches station master data. The gateway always requests the full page (the
     *  dataset is ~20 rows) and does search, proximity and pagination itself. */
    fun findAll(country: String? = null): List<StationData> {
        val spec = rest.get().uri { builder ->
            builder.path("/station-data")
                .queryParam("pageSize", 100)
                .queryParam("offset", 0)
            if (country != null) builder.queryParam("country", country)
            builder.build()
        }
        return spec.retrieve().body(StationDataResponse::class.java)?.stations ?: emptyList()
    }
}