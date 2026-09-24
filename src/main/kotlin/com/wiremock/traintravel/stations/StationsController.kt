package com.wiremock.traintravel.stations

import com.wiremock.traintravel.common.BadRequestException
import com.wiremock.traintravel.common.CollectionDto
import com.wiremock.traintravel.common.validatePagination
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class StationsController(private val service: StationsService) {

    @GetMapping("/stations")
    fun stations(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") limit: Int,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) country: String?,
        @RequestParam(required = false) coordinates: String?,
    ): ResponseEntity<CollectionDto<StationDto>> {
        validatePagination(page, limit)
        if (country != null && !country.matches(Regex("^[A-Za-z]{2}$"))) {
            throw BadRequestException("Parameter 'country' must be a 2-letter ISO country code.")
        }
        val coords = coordinates?.let(::parseCoordinates)
        val body = service.searchStations(
            search = search?.takeIf { it.isNotBlank() },
            country = country?.uppercase(),
            coordinates = coords,
            page = page,
            limit = limit,
        )
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
            .body(body)
    }

    private fun parseCoordinates(raw: String): Pair<Double, Double> {
        val parts = raw.split(",")
        if (parts.size != 2) throw BadRequestException("Parameter 'coordinates' must be '<latitude>,<longitude>'.")
        val lat = parts[0].trim().toDoubleOrNull() ?: throw BadRequestException("Parameter 'coordinates' has an invalid latitude.")
        val lon = parts[1].trim().toDoubleOrNull() ?: throw BadRequestException("Parameter 'coordinates' has an invalid longitude.")
        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
            throw BadRequestException("Parameter 'coordinates' is out of range.")
        }
        return lat to lon
    }
}