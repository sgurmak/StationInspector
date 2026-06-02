package com.example.stationinspector.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.stationinspector.data.local.converter.Converters
import com.example.stationinspector.data.local.dao.PhotoDao
import com.example.stationinspector.data.local.dao.StationDao
import com.example.stationinspector.data.local.entity.PhotoEntity
import com.example.stationinspector.data.local.entity.StationEntity
import com.example.stationinspector.data.local.entity.RouteCacheEntity
import com.example.stationinspector.data.local.entity.PoiEntity
import com.example.stationinspector.data.local.dao.RouteCacheDao
import com.example.stationinspector.data.local.dao.PoiDao
import com.example.stationinspector.data.local.entity.ShortcutEntity
import com.example.stationinspector.data.local.dao.ShortcutDao

@Database(
    entities = [StationEntity::class, PhotoEntity::class, RouteCacheEntity::class, PoiEntity::class, ShortcutEntity::class],
    version = 8,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract val stationDao: StationDao
    abstract val photoDao: PhotoDao
    abstract val routeCacheDao: RouteCacheDao
    abstract val poiDao: PoiDao
    abstract val shortcutDao: ShortcutDao

    companion object {
        const val DATABASE_NAME = "station_inspector_db"

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE photos ADD COLUMN assignedDate TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE stations ADD COLUMN latitude REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE stations ADD COLUMN longitude REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `route_cache` (`id` TEXT NOT NULL, `originLat` REAL NOT NULL, `originLon` REAL NOT NULL, `destLat` REAL NOT NULL, `destLon` REAL NOT NULL, `distanceMeters` REAL NOT NULL, `durationSeconds` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                database.execSQL("CREATE TABLE IF NOT EXISTS `pois` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `label` TEXT NOT NULL)")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE route_cache ADD COLUMN geometry TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `shortcuts` (`id` TEXT NOT NULL, `label` TEXT NOT NULL, `customName` TEXT, `poiItemJson` TEXT, `isNew` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Not needed since we rely on destructive migration or we can add it safely if we want, but version is bumped to 8.
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE stations ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0")
                // ShortcutEntity declares isRoundTrip, but MIGRATION_5_6 created the
                // shortcuts table without it and 6->7 was a no-op. Without this ALTER,
                // upgraders reach v8 with a schema that fails Room validation — only
                // the destructive fallback was hiding it (by wiping all data).
                database.execSQL("ALTER TABLE shortcuts ADD COLUMN isRoundTrip INTEGER NOT NULL DEFAULT 0")
                database.execSQL("DROP TABLE pois")
                database.execSQL("CREATE TABLE IF NOT EXISTS `pois` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `city` TEXT, `address` TEXT, `region` TEXT, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `inspectionDate` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }
    }
}
