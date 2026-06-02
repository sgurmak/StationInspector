package com.example.stationinspector.data.local.dao

import androidx.room.*
import com.example.stationinspector.data.local.entity.StationEntity
import com.example.stationinspector.data.local.entity.StationWithSplitCounts
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {
    @Query("SELECT * FROM stations")
    fun getAllStations(): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations")
    suspend fun getAllStationsSync(): List<StationEntity>

    /**
     * Returns each station with two split photo counts:
     *   regularCount = CLIENT_REPORT photos
     *   issueCount   = INTERNAL_DEFECT photos
     * Room re-emits whenever stations OR photos change (both tables are observed).
     */
    @Query("""
        SELECT s.*,
               COUNT(CASE WHEN p.type = 'CLIENT_REPORT'   THEN 1 END) AS regularCount,
               COUNT(CASE WHEN p.type = 'INTERNAL_DEFECT' THEN 1 END) AS issueCount
        FROM stations s
        LEFT JOIN photos p ON s.id = p.stationId
        GROUP BY s.id
        ORDER BY s.orderIndex ASC
    """)
    fun getStationsWithSplitCounts(): Flow<List<StationWithSplitCounts>>

    @Query("SELECT * FROM stations WHERE id = :stationId")
    fun getStationById(stationId: Long): Flow<StationEntity?>

    @Query("SELECT * FROM stations WHERE id = :stationId")
    suspend fun getStationByIdSync(stationId: Long): StationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStation(station: StationEntity): Long

    @Update
    suspend fun updateStation(station: StationEntity)

    @Update
    suspend fun updateStations(stations: List<StationEntity>)

    @Query("UPDATE stations SET latitude = :lat, longitude = :lon WHERE id = :id")
    suspend fun updateStationCoordinates(id: Long, lat: Double, lon: Double)

    @Query("UPDATE stations SET orderIndex = :orderIndex WHERE id = :id")
    suspend fun updateStationOrder(id: Long, orderIndex: Int)

    @Transaction
    suspend fun updateStationOrders(orders: List<Pair<Long, Int>>) {
        for (order in orders) {
            updateStationOrder(order.first, order.second)
        }
    }

    @Delete
    suspend fun deleteStation(station: StationEntity)

    @Query("DELETE FROM stations")
    suspend fun deleteAllStations()
}