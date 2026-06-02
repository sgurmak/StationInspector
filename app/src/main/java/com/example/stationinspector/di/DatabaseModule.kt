package com.example.stationinspector.di

import android.app.Application
import androidx.room.Room
import android.content.Context
import com.example.stationinspector.BuildConfig
import com.example.stationinspector.data.local.AppDatabase
import com.example.stationinspector.data.local.dao.PhotoDao
import com.example.stationinspector.data.local.dao.StationDao
import com.example.stationinspector.data.local.dao.RouteCacheDao
import com.example.stationinspector.data.local.dao.PoiDao
import com.example.stationinspector.data.local.dao.ShortcutDao
import com.example.stationinspector.data.remote.OrsApiService
import com.example.stationinspector.data.repository.PoiRepositoryImpl
import com.example.stationinspector.data.repository.PreferencesRepositoryImpl
import com.example.stationinspector.data.repository.RouteRepositoryImpl
import com.example.stationinspector.data.repository.ShortcutRepositoryImpl
import com.example.stationinspector.data.repository.StationRepositoryImpl
import com.example.stationinspector.domain.repository.PoiRepository
import com.example.stationinspector.domain.repository.PreferencesRepository
import com.example.stationinspector.domain.repository.RouteRepository
import com.example.stationinspector.domain.repository.ShortcutRepository
import com.example.stationinspector.domain.repository.StationRepository
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(app: Application): AppDatabase {
        return Room.databaseBuilder(
            app,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8)
        .apply {
            // Destructive fallback only in debug builds. A release build must
            // never silently wipe inspection data — a missing migration path
            // should surface as a crash to investigate, not as data loss.
            if (BuildConfig.DEBUG) fallbackToDestructiveMigration()
        }
        .build()
    }

    @Provides
    @Singleton
    fun provideStationDao(db: AppDatabase): StationDao {
        return db.stationDao
    }

    @Provides
    @Singleton
    fun providePhotoDao(db: AppDatabase): PhotoDao {
        return db.photoDao
    }

    @Provides
    @Singleton
    fun provideRouteCacheDao(db: AppDatabase): RouteCacheDao {
        return db.routeCacheDao
    }

    @Provides
    @Singleton
    fun providePoiDao(db: AppDatabase): PoiDao {
        return db.poiDao
    }

    @Provides
    @Singleton
    fun provideShortcutDao(db: AppDatabase): ShortcutDao {
        return db.shortcutDao
    }

    @Provides
    @Singleton
    fun provideStationRepository(
        stationDao: StationDao,
        photoDao: PhotoDao,
        @ApplicationContext context: Context
    ): StationRepository {
        return StationRepositoryImpl(stationDao, photoDao, context)
    }

    @Provides
    @Singleton
    fun provideRouteRepository(
        routeCacheDao: RouteCacheDao,
        orsApiService: OrsApiService,
        stationDao: StationDao
    ): RouteRepository {
        return RouteRepositoryImpl(routeCacheDao, orsApiService, stationDao)
    }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    // ── Singletons added by Step 5 (Repository layer for DAO/DataStore) ───

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideShortcutRepository(
        shortcutDao: ShortcutDao,
        gson: Gson
    ): ShortcutRepository = ShortcutRepositoryImpl(shortcutDao, gson)

    @Provides
    @Singleton
    fun providePoiRepository(poiDao: PoiDao): PoiRepository =
        PoiRepositoryImpl(poiDao)

    @Provides
    @Singleton
    fun providePreferencesRepository(
        dataStore: DataStore<Preferences>
    ): PreferencesRepository = PreferencesRepositoryImpl(dataStore)
}
