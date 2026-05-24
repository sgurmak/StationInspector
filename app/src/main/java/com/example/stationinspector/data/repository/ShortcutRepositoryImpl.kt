package com.example.stationinspector.data.repository

import com.example.stationinspector.data.local.dao.ShortcutDao
import com.example.stationinspector.data.local.entity.ShortcutEntity
import com.example.stationinspector.data.mapper.toDomain
import com.example.stationinspector.domain.model.PoiLocation
import com.example.stationinspector.domain.model.Shortcut
import com.example.stationinspector.domain.repository.ShortcutRepository
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShortcutRepositoryImpl @Inject constructor(
    private val dao: ShortcutDao,
    private val gson: Gson
) : ShortcutRepository {

    override fun observeShortcuts(): Flow<List<Shortcut>> =
        dao.getAllShortcuts().map { entities -> entities.map { it.toDomain(gson) } }

    override suspend fun count(): Int = dao.getShortcutCount()

    override suspend fun insertAll(shortcuts: List<Shortcut>) {
        val entities = shortcuts.map { shortcut ->
            ShortcutEntity(
                id          = shortcut.id,
                label       = shortcut.label,
                customName  = shortcut.customName,
                poiItemJson = shortcut.location?.let { gson.toJson(it) },
                isNew       = shortcut.isNew,
                isRoundTrip = shortcut.isRoundTrip
            )
        }
        dao.insertShortcuts(entities)
    }

    override suspend fun updateShortcut(
        id: String,
        location: PoiLocation?,
        customName: String?,
        isNew: Boolean
    ) {
        val json = location?.let { gson.toJson(it) }
        dao.updateShortcut(id, json, customName, isNew)
    }

    override suspend fun insertPreset(location: PoiLocation?, customName: String?): String {
        val id = UUID.randomUUID().toString()
        val json = location?.let { gson.toJson(it) }
        val isNew = location == null
        dao.insertShortcut(
            ShortcutEntity(
                id = id,
                label = "Preset",
                customName = customName,
                poiItemJson = json,
                isNew = isNew
            )
        )
        return id
    }

    override suspend fun deleteShortcut(id: String) = dao.deleteShortcut(id)

    override suspend fun clearOldBlankShortcuts() = dao.clearOldBlankShortcuts()

    override suspend fun ensureDefaults() {
        if (dao.getShortcutCount() == 0) {
            dao.insertShortcuts(
                listOf(
                    ShortcutEntity(Shortcut.ID_HOME, "Home", null, null, isNew = true),
                    ShortcutEntity(Shortcut.ID_WORK, "Work", null, null, isNew = true)
                )
            )
        }
    }
}
