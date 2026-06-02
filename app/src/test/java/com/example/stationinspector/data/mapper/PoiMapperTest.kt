package com.example.stationinspector.data.mapper

import com.example.stationinspector.domain.model.Poi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for [Poi.toEntity]/[PoiEntity.toDomain].
 *
 * Lives in the same package as the mapper so the `internal` extension
 * functions are accessible.
 */
class PoiMapperTest {

    @Test
    fun `round trips a fully populated poi`() {
        val original = Poi(
            id = "poi-1",
            name = "Kolín",
            city = "Kolín",
            address = "Nádraží 1",
            region = "Středočeský",
            latitude = 50.02,
            longitude = 15.20,
            inspectionDate = LocalDate.of(2026, 2, 1),
            orderIndex = 3
        )

        val result = original.toEntity().toDomain()

        assertEquals(original, result)
    }

    @Test
    fun `round trips a poi with null optional fields`() {
        val original = Poi(
            id = "poi-2",
            name = "Praha",
            city = null,
            address = null,
            region = null,
            latitude = 50.08,
            longitude = 14.43,
            inspectionDate = LocalDate.of(2026, 2, 1),
            orderIndex = 0
        )

        val result = original.toEntity().toDomain()

        assertEquals(original, result)
        assertNull(result.city)
        assertNull(result.address)
        assertNull(result.region)
    }
}
