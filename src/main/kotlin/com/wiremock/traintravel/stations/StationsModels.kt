package com.wiremock.traintravel.stations

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.UUID

// --- Downstream "European Rail Reference Data" models (camelCase, per-service convention) ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class StationDataResponse(
    val stations: List<StationData> = emptyList(),
    val offset: Int = 0,
    val pageSize: Int = 10,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class StationData(
    val stationCode: String,
    val stationName: String,
    val addressLines: List<String> = emptyList(),
    val countryIsoCode: String,
    val geo: Geo = Geo(0.0, 0.0),
    val tzId: String,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Geo(val latitude: Double, val longitude: Double)
}

// --- Public API models (snake_case via the global Jackson strategy) ---

data class StationDto(
    val id: UUID,
    val name: String,
    val address: String,
    val countryCode: String,
    val timezone: String,
) {
    companion object {
        fun from(station: StationData, stationUuid: UUID): StationDto = StationDto(
            id = stationUuid,
            name = station.stationName,
            address = station.addressLines.filter { it.isNotBlank() }.joinToString(", "),
            countryCode = station.countryIsoCode,
            timezone = station.tzId,
        )
    }
}