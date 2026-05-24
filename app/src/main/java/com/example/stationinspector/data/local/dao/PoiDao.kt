package com.example.stationinspector.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.stationinspector.data.local.entity.PoiEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PoiDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoi(poi: PoiEntity)

    @Query("SELECT * FROM pois WHERE inspectionDate = :date ORDER BY orderIndex ASC")
    fun getPoisForDate(date: java.time.LocalDate): Flow<List<PoiEntity>>

    @Query("SELECT * FROM pois WHERE inspectionDate = :date ORDER BY orderIndex ASC")
    suspend fun getPoisForDateSync(date: java.time.LocalDate): List<PoiEntity>

    @Query("UPDATE pois SET orderIndex = :orderIndex WHERE id = :id")
    suspend fun updatePoiOrder(id: String, orderIndex: Int)

    @androidx.room.Transaction
    suspend fun updatePoiOrders(orders: List<Pair<String, Int>>) {
        for (order in orders) {
            updatePoiOrder(order.first, order.second)
        }
    }

    @Query("DELETE FROM pois WHERE id = :id")
    suspend fun deletePoi(id: String)

    @Query("DELETE FROM pois WHERE name = :name AND inspectionDate = :date")
    suspend fun deletePoisByNameAndDate(name: String, date: java.time.LocalDate)
}
