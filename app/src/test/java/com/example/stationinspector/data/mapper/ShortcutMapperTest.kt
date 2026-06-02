package com.example.stationinspector.data.mapper

import com.example.stationinspector.data.local.entity.ShortcutEntity
import com.example.stationinspector.domain.model.PoiLocation
import com.example.stationinspector.domain.model.Shortcut
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [ShortcutEntity.toDomain]/[Shortcut.toEntity].
 *
 * Lives in the same package as the mapper so the `internal` extension
 * functions are accessible. Requires `testOptions.unitTests.isReturnDefaultValues
 * = true` so that android.util.Log calls inside the mapper return defaults
 * instead of throwing.
 */
class ShortcutMapperTest {

    private val gson = Gson()

    @Test
    fun `round trips a shortcut with a location`() {
        val original = Shortcut(
            id = "1",
            label = "Home",
            customName = "My place",
            location = PoiLocation(
                id = "loc-1",
                name = "Home",
                city = "Praha",
                address = "Main 1",
                region = "CZ",
                latitude = 50.0,
                longitude = 14.0
            ),
            isNew = false,
            isRoundTrip = true
        )

        val result = original.toEntity(gson).toDomain(gson)

        assertEquals(original, result)
    }

    @Test
    fun `null location maps to null json and back to null`() {
        val original = Shortcut(
            id = "2",
            label = "Work",
            customName = null,
            location = null,
            isNew = true,
            isRoundTrip = false
        )

        val entity = original.toEntity(gson)
        assertNull(entity.poiItemJson)

        val result = entity.toDomain(gson)
        assertNull(result.location)
        assertEquals(original, result)
    }

    @Test
    fun `legacy json with extra fields is parsed and unknown fields ignored`() {
        val legacyJson = """
            {"id":"x","name":"Home","city":"Praha","address":"Main 1","region":"CZ",
             "latitude":50.0,"longitude":14.0,"isHidden":false,"uniqueId":"x","orderIndex":3}
        """.trimIndent()

        val entity = ShortcutEntity(
            id = "x",
            label = "Home",
            customName = null,
            poiItemJson = legacyJson,
            isNew = false,
            isRoundTrip = false
        )

        val location = entity.toDomain(gson).location

        assertNotNull(location)
        assertEquals("x", location!!.id)
        assertEquals("Home", location.name)
        assertEquals("Praha", location.city)
        assertEquals("Main 1", location.address)
        assertEquals("CZ", location.region)
        assertEquals(50.0, location.latitude, 0.0)
        assertEquals(14.0, location.longitude, 0.0)
    }

    @Test
    fun `malformed json yields null location instead of throwing`() {
        val entity = ShortcutEntity(
            id = "broken",
            label = "Broken",
            customName = null,
            poiItemJson = "not-json{",
            isNew = false,
            isRoundTrip = false
        )

        val result = entity.toDomain(gson)

        assertNull(result.location)
    }
}
