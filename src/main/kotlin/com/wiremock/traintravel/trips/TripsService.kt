package com.wiremock.traintravel.trips

import com.wiremock.traintravel.common.BadRequestException
import com.wiremock.traintravel.common.CollectionDto
import com.wiremock.traintravel.common.LinksDto
import com.wiremock.traintravel.common.paginationLinks
import com.wiremock.traintravel.config.AppProperties
import com.wiremock.traintravel.stations.StationsService
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.UUID

@Service
class TripsService(
    private val client: TripsClient,
    private val stationsService: StationsService,
    private val props: AppProperties,
) {

    fun findTrips(
        originUuid: UUID,
        destinationUuid: UUID,
        date: LocalDate,
        bicycles: Boolean,
        dogs: Boolean,
        page: Int,
        limit: Int,
    ): CollectionDto<TripDto> {
        val origin = stationsService.stationCodeFor(originUuid)
            ?: throw BadRequestException("Unknown origin station: $originUuid")
        val destination = stationsService.stationCodeFor(destinationUuid)
            ?: throw BadRequestException("Unknown destination station: $destinationUuid")

        val connections = client.findConnections(origin, destination, date, page, limit).connections

        // Allowance filtering happens here, post-pagination. Safe because each route has at
        // most 4 connections, below the minimum page size, so downstream pages never split a route.
        val filtered = connections.filter { connection ->
            (!bicycles || connection.facilities.bicyclesAllowed) && (!dogs || connection.facilities.dogsAllowed)
        }
        val trips = filtered.map { TripDto.from(it, props.publicBaseUrl) }

        // No downstream total is available; a full page means another page may exist.
        val total = if (connections.size == limit) page * limit + 1 else (page - 1) * limit + filtered.size
        val links: LinksDto = paginationLinks(
            props.publicBaseUrl, "/trips",
            mapOf(
                "origin" to originUuid.toString(),
                "destination" to destinationUuid.toString(),
                "date" to date.toString(),
                "bicycles" to bicycles.toString(),
                "dogs" to dogs.toString(),
            ),
            page, limit, total,
        )
        return CollectionDto(trips, links)
    }
}