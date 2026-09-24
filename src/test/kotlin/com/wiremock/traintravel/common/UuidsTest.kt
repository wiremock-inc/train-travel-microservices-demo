package com.wiremock.traintravel.common

import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@ExtendWith(MockKExtension::class)
class UuidsTest {

    @Test
    fun `v5 matches the RFC 4122 test vector`() {
        // Known vector: uuidv5(DNS namespace, "python.org") = 886313e1-3b8a-5372-9b90-0c9aee199e5d
        val dnsNamespace = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
        assertEquals(
            UUID.fromString("886313e1-3b8a-5372-9b90-0c9aee199e5d"),
            Uuids.v5(dnsNamespace, "python.org"),
        )
    }

    @Test
    fun `station UUIDs are stable and distinct`() {
        val first = Uuids.stationUuid("DE-BER-HBF")
        val second = Uuids.stationUuid("DE-BER-HBF")
        val other = Uuids.stationUuid("DE-MUC-HBF")

        assertEquals(first, second, "the same station code must always yield the same UUID")
        assertNotEquals(first, other)
    }

    @Test
    fun `trip UUIDs depend on route and departure`() {
        val berlinMunich0800 = Uuids.tripUuid("DE-BER-HBF", "DE-MUC-HBF", "2026-10-01T08:00:00Z")
        val sameAgain = Uuids.tripUuid("DE-BER-HBF", "DE-MUC-HBF", "2026-10-01T08:00:00Z")
        val differentDay = Uuids.tripUuid("DE-BER-HBF", "DE-MUC-HBF", "2026-10-02T08:00:00Z")

        assertEquals(berlinMunich0800, sameAgain)
        assertNotEquals(berlinMunich0800, differentDay)
    }

    @Test
    fun `generated ids are version 5`() {
        val uuid = Uuids.stationUuid("FR-PAR-NOT")
        assertEquals(5, uuid.version())
        assertEquals(2, uuid.variant())
    }
}