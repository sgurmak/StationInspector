package com.example.stationinspector.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.stationinspector.data.local.AppDatabase
import com.example.stationinspector.data.storage.FileStorageManager
import com.example.stationinspector.domain.model.Photo
import com.example.stationinspector.domain.model.PhotoType
import com.example.stationinspector.domain.model.PhotoZone
import com.example.stationinspector.domain.model.Station
import com.example.stationinspector.domain.model.StationStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

/**
 * Runtime verification (real SQLite + real filesDir) that clearing all data
 * also removes the photo FILES — the regression guard for the storage leak
 * where an in-app "clear" wiped the database but left ~GBs of orphaned JPEGs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StationRepositoryRoomTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var fileStorage: FileStorageManager
    private lateinit var repository: StationRepositoryImpl

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fileStorage = FileStorageManager(context)
        fileStorage.clearAllPhotoFiles() // isolate from other tests' files
        repository = StationRepositoryImpl(db.stationDao, db.photoDao, fileStorage, context)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `clearAllData deletes photo files from disk, not just rows`() = runBlocking {
        val path = fileStorage.savePhoto(byteArrayOf(1, 2, 3))!!
        assertTrue("file should exist before clear", File(path).exists())

        val stationId = repository.saveStation(
            Station(0, "Kolín", "", 50.0, 14.0, LocalDate.now(), StationStatus.PENDING)
        )
        repository.savePhoto(
            Photo(0, stationId, PhotoZone.ENTRANCE, PhotoType.CLIENT_REPORT, path, 0L, "", false, "")
        )

        repository.clearAllData()

        assertFalse("photo file must be deleted from disk", File(path).exists())
        assertEquals(0, repository.getAllStations().first().size)
        assertEquals(0, repository.getAllPhotos().first().size)
    }

    @Test
    fun `clearAllPhotoFiles sweeps orphaned files too`() = runBlocking {
        val p1 = fileStorage.savePhoto(byteArrayOf(1))!!
        val p2 = fileStorage.savePhoto(byteArrayOf(2))!!

        val deleted = fileStorage.clearAllPhotoFiles()

        assertEquals(2, deleted)
        assertFalse(File(p1).exists())
        assertFalse(File(p2).exists())
    }
}
