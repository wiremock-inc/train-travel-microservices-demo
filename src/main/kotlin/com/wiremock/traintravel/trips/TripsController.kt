package com.wiremock.traintravel.trips

import com.wiremock.traintravel.common.BadRequestException
import com.wiremock.traintravel.common.CollectionDto
import com.wiremock.traintravel.common.requireUuid
import com.wiremock.traintravel.common.validatePagination
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.format.DateTimeParseException

@RestController
class TripsController(private val service: TripsService) {

    @GetMapping("/trips")
    fun trips(
        @RequestParam origin: String,
        @RequestParam destination: String,
        @RequestParam date: String,
        @RequestParam(required = false, defaultValue = "false") bicycles: Boolean,
        @RequestParam(required = false, defaultValue = "false") dogs: Boolean,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") limit: Int,
    ): ResponseEntity<CollectionDto<TripDto>> {
        validatePagination(page, limit)
        val originUuid = requireUuid(origin, "origin")
        val destinationUuid = requireUuid(destination, "destination")
        val travelDate = try {
            LocalDate.parse(date.substringBefore("T"))
        } catch (e: DateTimeParseException) {
            throw BadRequestException("Parameter 'date' must be an ISO 8601 date or date-time.")
        }
        if (travelDate.isBefore(LocalDate.now())) {
            throw BadRequestException("The search date specified is in the past.")
        }
        val body = service.findTrips(originUuid, destinationUuid, travelDate, bicycles, dogs, page, limit)
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "max-age=60")
            .body(body)
    }
}