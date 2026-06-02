package com.example.stationinspector.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.stationinspector.data.local.AppDatabase
import com.example.stationinspector.domain.model.PoiLocation
import com.example.stationinspector.domain.model.Shortcut
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runtime verification of the shortcut data path against a REAL (in-memory)
 * SQLite database via Robolectric — exercising the Room schema, the Gson
 * location (de)serialization, and the isRoundTrip column that the P0 migration
 * fix restored.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric 4.13 has no SDK 35 image; 34 exercises the same Room/SQLite.
class ShortcutRepositoryRoomTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ShortcutRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ShortcutRepositoryImpl(db.shortcutDao, Gson())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `round-trips a shortcut including location and isRoundTrip through SQLite`() = runBlocking {
        val original = Shortcut(
            id = "1",
            label = "Home",
            customName = "My place",
            location = PoiLocation("loc-1", "Home", "Praha", "Main 1", "CZ", 50.0, 14.0),
            isNew = false,
            isRoundTrip = true
        )
        repository.insertAll(listOf(original))

        val loaded = repository.observeShortcuts().first()

        assertEquals(1, loaded.size)
        assertEquals(original, loaded[0])
        assertTrue(loaded[0].isRoundTrip)
        assertEquals(50.0, loaded[0].location!!.latitude, 0.0)
    }

    @Test
    fun `ensureDefaults seeds Home and Work exactly once`() = runBlocking {
        repository.ensureDefaults()
        repository.ensureDefaults() // must be idempotent

        val shortcuts = repository.observeShortcuts().first()

        assertEquals(2, shortcuts.size)
        assertTrue(shortcuts.any { it.id == Shortcut.ID_HOME })
        assertTrue(shortcuts.any { it.id == Shortcut.ID_WORK })
    }

    @Test
    fun `updateShortcut can clear a bound location`() = runBlocking {
        repository.insertAll(
            listOf(
                Shortcut(
                    id = "2", label = "Work", customName = null,
                    location = PoiLocation("l", "Work", null, null, null, 49.0, 16.0),
                    isNew = false, isRoundTrip = false
                )
            )
        )

        repository.updateShortcut("2", location = null, customName = null, isNew = true)

        val loaded = repository.observeShortcuts().first().first { it.id == "2" }
        assertNull(loaded.location)
        assertTrue(loaded.isNew)
    }
}
