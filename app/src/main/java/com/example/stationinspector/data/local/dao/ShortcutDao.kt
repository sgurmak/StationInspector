package com.example.stationinspector.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.stationinspector.data.local.entity.ShortcutEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShortcutDao {
    @Query("SELECT * FROM shortcuts ORDER BY id ASC")
    fun getAllShortcuts(): Flow<List<ShortcutEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShortcut(shortcut: ShortcutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShortcuts(shortcuts: List<ShortcutEntity>)

    @Query("UPDATE shortcuts SET poiItemJson = :poiItemJson, customName = :customName, isNew = :isNew WHERE id = :id")
    suspend fun updateShortcut(id: String, poiItemJson: String?, customName: String?, isNew: Boolean)

    @Query("SELECT COUNT(*) FROM shortcuts")
    suspend fun getShortcutCount(): Int

    @Query("DELETE FROM shortcuts WHERE id = :id")
    suspend fun deleteShortcut(id: String)

    @Query("DELETE FROM shortcuts WHERE isNew = 1 AND id NOT IN ('1', '2')")
    suspend fun clearOldBlankShortcuts()
}
