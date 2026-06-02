package com.example.stationinspector.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies MIGRATION_7_8 against a real SQLite database. Historical schemas
 * (v1–v7) were never exported, so the pre-migration state is reconstructed via
 * raw SQL and the migration is run directly — this is the regression guard for
 * the P0 fix that restores the shortcuts.isRoundTrip column.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private fun openV7Database(): SupportSQLiteDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Schema as it stood at version 7 for the tables 7->8 touches.
                    db.execSQL("CREATE TABLE IF NOT EXISTS `shortcuts` (`id` TEXT NOT NULL, `label` TEXT NOT NULL, `customName` TEXT, `poiItemJson` TEXT, `isNew` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `stations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `address` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `inspectionDate` TEXT, `status` TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `pois` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `label` TEXT NOT NULL)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): Set<String> {
        val names = mutableSetOf<String>()
        db.query("PRAGMA table_info(`$table`)").use { c ->
            val nameIdx = c.getColumnIndex("name")
            while (c.moveToNext()) names.add(c.getString(nameIdx))
        }
        return names
    }

    @Test
    fun `7_8 adds isRoundTrip and orderIndex, recreates pois, preserves shortcut data`() {
        val db = openV7Database()
        db.execSQL("INSERT INTO shortcuts (id, label, customName, poiItemJson, isNew) VALUES ('1','Home',NULL,NULL,1)")

        AppDatabase.MIGRATION_7_8.migrate(db)

        // The bug fix: shortcuts gains isRoundTrip; stations gains orderIndex.
        assertTrue("shortcuts must gain isRoundTrip", "isRoundTrip" in columns(db, "shortcuts"))
        assertTrue("stations must gain orderIndex", "orderIndex" in columns(db, "stations"))
        // pois is rebuilt with the new string-keyed schema.
        val poiCols = columns(db, "pois")
        assertTrue("pois must be recreated with name/inspectionDate/orderIndex",
            poiCols.containsAll(listOf("name", "inspectionDate", "orderIndex")))

        // Existing shortcut survives the migration; isRoundTrip defaults to 0.
        db.query("SELECT id, isRoundTrip FROM shortcuts WHERE id = '1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("1", c.getString(0))
            assertEquals(0, c.getInt(1))
        }
        db.close()
    }
}
