package com.wiremock.traintravel.stations

import com.wiremock.traintravel.common.BadRequestException
import com.wiremock.traintravel.common.CollectionDto
import com.wiremock.traintravel.common.LinksDto
import com.wiremock.traintravel.common.Uuids
import com.wiremock.traintravel.common.paginationLinks
import com.wiremock.traintravel.config.AppProperties
import org.springframework.stereotype.Service
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Service
class StationsService(private val client: StationsClient, private val props: AppProperties) {

    /** Searches stations by name/address, sorts by name (or distance when coordinates given), paginates. */
    fun searchStations(
        search: String?,
        country: String?,
        coordinates: Pair<Double, Double>?,
        page: Int,
        limit: Int,
    ): CollectionDto<StationDto> {
        val matching = client.findAll(country)
            .filter { station ->
                search.isNullOrBlank() ||
                    station.stationName.contains(search, ignoreCase = true) ||
                    station.addressLines.any { it.contains(search, ignoreCase = true) }
            }

        val sorted = when {
            coordinates != null -> matching.sortedBy { distanceKm(it.geo, coordinates) }
            else -> matching.sortedBy { it.stationName.lowercase() }
        }

        val pageItems = sorted.drop((page - 1) * limit).take(limit)
        val links = paginationLinks(
            props.publicBaseUrl, "/stations",
            mapOf("search" to search, "country" to country),
            page, limit, sorted.size,
        )
        return CollectionDto(pageItems.map { StationDto.from(it, Uuids.stationUuid(it.stationCode)) }, links)
    }

    /** Resolves a public station UUID back to its station code; null when unknown. */
    fun stationCodeFor(stationUuid: UUID): String? =
        client.findAll().firstOrNull { Uuids.stationUuid(it.stationCode) == stationUuid }?.stationCode

    /** Great-circle distance for proximity sorting. */
    private fun distanceKm(geo: StationData.Geo, from: Pair<Double, Double>): Double {
        val lat1 = Math.toRadians(from.first)
        val lon1 = Math.toRadians(from.second)
        val lat2 = Math.toRadians(geo.latitude)
        val lon2 = Math.toRadians(geo.longitude)
        val dLat = lat2 - lat1
        val dLon = lon2 - lon1
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 6371.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}