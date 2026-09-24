package com.wiremock.traintravel.common

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/**
 * Deterministic UUID v5 derivation. Pure functions, no state: the same station or
 * connection always yields the same public id, across restarts and clients.
 */
object Uuids {

    /** Fixed namespace for the demo estate - change it and every public id changes. */
    private val NAMESPACE: UUID = UUID.fromString("9d2f3a6c-1e8b-4b8a-b0f3-f7a2c1e8d470")

    fun stationUuid(stationCode: String): UUID = v5(NAMESPACE, stationCode)

    fun tripUuid(fromStationCode: String, toStationCode: String, scheduledDepartureIso: String): UUID =
        v5(NAMESPACE, "$fromStationCode|$toStationCode|$scheduledDepartureIso")

    fun v5(namespace: UUID, name: String): UUID {
        val digest = MessageDigest.getInstance("SHA-1").run {
            update(namespace.bytes())
            update(name.toByteArray(Charsets.UTF_8))
            digest()
        }
        // Per RFC 4122 §4.3: set version to 5 and variant to RFC 4122
        digest[6] = ((digest[6].toInt() and 0x0f) or 0x50).toByte()
        digest[8] = ((digest[8].toInt() and 0x3f) or 0x80).toByte()
        val buffer = ByteBuffer.wrap(digest, 0, 16)
        val most = buffer.long
        val least = buffer.long
        return UUID(most, least)
    }

    private fun UUID.bytes(): ByteArray =
        ByteBuffer.allocate(16).putLong(mostSignificantBits).putLong(leastSignificantBits).array()
}